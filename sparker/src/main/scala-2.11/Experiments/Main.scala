package Experiments

import java.util.Calendar

import BlockBuildingMethods._
import BlockRefinementMethods.PruningMethods.{CNPFor, PruningUtils, WNPFor}
import BlockRefinementMethods.{BlockFiltering, BlockPurging}
import DataStructures.KeysCluster
import Utilities.Converters
import Wrappers.{CSVWrapper, SerializedObjectLoader}
import org.apache.spark.util.SizeEstimator
import org.apache.spark.{SparkConf, SparkContext}

/**
  * Created by Luca on 24/02/2017.
  */
object Main {
  def main(args: Array[String]) {
    val purgingRatio = 1.0
    val filteringRatio = 0.8
    val thresholdType = WNPFor.ThresholdTypes.MAX_FRACT_2
    val weightType = PruningUtils.WeightTypes.CBS
    val hashNum = 16
    val clusterThreshold = 0.8

    /*val memoryHeap = args(0)
    val memoryStack = args(1)
    val pathDataset1 = args(2)
    val pathDataset2 = args(3)
    val pathGt = args(4)*/


    val memoryHeap = 15
    val memoryStack = 5
    val pathDataset1 = "C:/Users/Luca/Desktop/UNI/BlockingFramework/datasets/movies/profiles/dataset1"
    val pathDataset2 = "C:/Users/Luca/Desktop/UNI/BlockingFramework/datasets/movies/profiles/dataset2"
    val pathGt = "C:/Users/Luca/Desktop/UNI/BlockingFramework/datasets/movies/groundtruth"



    println("Heap "+memoryHeap+"g")
    println("Stack "+memoryStack+"g")
    println("First dataset path "+pathDataset1)
    println("Second dataset path "+pathDataset2)
    println("Groundtruth path "+pathGt)
    println("Threshold type "+thresholdType)
    println()


    val conf = new SparkConf()
      .setAppName("Main")
      .setMaster("local[*]")
      .set("spark.executor.memory", memoryHeap+"g")
      .set("spark.network.timeout", "10000s")
      .set("spark.executor.heartbeatInterval", "40s")
      .set("spark.default.parallelism", "32")
      .set("spark.executor.extraJavaOptions", "-Xss"+memoryStack+"g")
      .set("spark.local.dir", "/data2/sparkTmp/")
      .set("spark.driver.maxResultSize", "10g")

    val sc = new SparkContext(conf)

    println("Start to loading profiles")
    val startTime = Calendar.getInstance();
    val dataset1 = SerializedObjectLoader.loadProfiles(pathDataset1)//CSVWrapper.loadProfiles(filePath = pathDataset1, header = true, realIDField = "id")
    val separatorID = dataset1.map(_.id).max()
    val dataset2 = SerializedObjectLoader.loadProfiles(pathDataset2, separatorID+1)//CSVWrapper.loadProfiles(filePath = pathDataset2, startIDFrom = separatorID+1, header = true, realIDField = "id")
    val maxProfileID = dataset2.map(_.id).max()

    val profiles = dataset1.union(dataset2)
    profiles.cache()
    val numProfiles = profiles.count()

    println("First dataset max ID "+separatorID)
    println("Max profiles id "+maxProfileID)
    val profilesTime = Calendar.getInstance()
    println("Number of profiles "+numProfiles)
    println("Time to load profiles "+(profilesTime.getTimeInMillis-startTime.getTimeInMillis)+" ms")
    println()

    println("Start to loading the groundtruth")
    val groundtruth = SerializedObjectLoader.loadGroundtruth(pathGt)//CSVWrapper.loadGroundtruth(filePath = pathGt, header = true)
    val gtNum = groundtruth.count()
    val realIdId1 = sc.broadcast(dataset1.map(p => (p.originalID, p.id)).collectAsMap())
    val realIdId2 = sc.broadcast(dataset2.map(p => (p.originalID, p.id)).collectAsMap())
    println("Start to generate the new groundtruth")
    val newGT = groundtruth.map(g => (realIdId1.value(g.firstEntityID), realIdId2.value(g.secondEntityID))).collect().toSet
    realIdId1.unpersist()
    realIdId2.unpersist()
    groundtruth.cache()
    println("Generation completed")
    groundtruth.unpersist()
    val gtTime = Calendar.getInstance()
    println("Time to generate the new groundtruth "+(gtTime.getTimeInMillis-profilesTime.getTimeInMillis)+" ms")
    println()


    println("Start to generate clusters")
    println("Number of hashes "+hashNum)
    println("Target threshold "+clusterThreshold)
    //val clusters = LSHTwitter4.clusterSimilarAttributes(profiles, hashNum, clusterThreshold, separatorID = separatorID)
    val clusters = //List(KeysCluster(0,List("d_2_starring", "d_1_actor name"),1), KeysCluster(1,List("d_2_title", "d_1_title"),1), KeysCluster(2,List("d_1_director name", "d_2_writer"),1), KeysCluster(3,List("tuttiTokenNonNeiCluster"),1))
    List(KeysCluster(0,List("d_2_starring", "d_1_actor name"),5.025564082512673E-6), KeysCluster(1,List("d_2_title", "d_1_title"),5.361338295373993E-5), KeysCluster(2,List("d_1_director name", "d_2_writer"),5.3570230016185796E-5), KeysCluster(3,List("tuttiTokenNonNeiCluster"),2.5389929205080563E-5))
    val clusterTime = Calendar.getInstance()
    println("Time to generate clusters "+(clusterTime.getTimeInMillis-gtTime.getTimeInMillis)+" ms")
    clusters.foreach(println)
    println()


    println("Start to generating blocks")
    /*def keys = "title,authors,journal,month,year,publication_type".split(",")
    val clusters = for(i <- 0 to keys.length-1) yield{
      KeysCluster(i, LSHTwitter.Settings.FIRST_DATASET_PREFIX+keys(i) :: LSHTwitter.Settings.SECOND_DATASET_PREFIX+keys(i) :: Nil)
    }
    val a = KeysCluster(keys.length, LSHTwitter.Settings.DEFAULT_CLUSTER_NAME :: Nil) :: clusters.toList
    println("Clusters "+a)*/
    val blocks = /*TokenBlocking.createBlocksCluster(profiles, separatorID, clusters)*/TokenBlocking.createBlocks(profiles, separatorID)
    val blocksEntropies = blocks.map(b => (b.blockID, b.entropy))
    blocks.cache()
    val numBlocks = blocks.count()
    profiles.unpersist()
    dataset1.unpersist()
    dataset2.unpersist()
    val blocksTime = Calendar.getInstance()
    println("Number of blocks "+numBlocks)
    println("Time to generate blocks "+(blocksTime.getTimeInMillis-clusterTime.getTimeInMillis)+" ms")
    println()

    println("Start to block purging, smooth factor "+purgingRatio)
    val blocksPurged = BlockPurging.blockPurging(blocks, purgingRatio)
    val numPurgedBlocks = blocksPurged.count()
    blocks.unpersist()
    val blocksPurgingTime = Calendar.getInstance()
    println("Number of blocks after purging "+numPurgedBlocks)
    println("Time to purging blocks "+(blocksPurgingTime.getTimeInMillis-blocksTime.getTimeInMillis)+" ms")
    println()


    println("Start to block filtering, factor "+filteringRatio)
    val profileBlocks = Converters.blocksToProfileBlocks(blocksPurged)
    val profileBlocksFiltered = BlockFiltering.blockFiltering(profileBlocks, filteringRatio)
    profileBlocksFiltered.cache()
    val blocksAfterFiltering = Converters.profilesBlockToBlocks(profileBlocksFiltered, separatorID)
    blocksAfterFiltering.cache()
    val numFilteredBlocks = blocksAfterFiltering.count()
    blocksPurged.unpersist()
    val blocksFilteringTime = Calendar.getInstance()
    println("Number of blocks after filtering "+numFilteredBlocks)
    println("Time to filtering blocks "+(blocksFilteringTime.getTimeInMillis-blocksPurgingTime.getTimeInMillis)+" ms")
    println()


    println("Start to pruning edges")
    //val blockIndexMap = blocksAfterFiltering.map(b => (b.blockID, b.profiles)).collectAsMap()
    val blockIndexMap = blocksAfterFiltering.map(b => (b.blockID, b.profiles)).collectAsMap()
    val blocksEntropiesMap = sc.broadcast(blocksEntropies.collectAsMap())

    println("Size of blockIndex "+SizeEstimator.estimate(blockIndexMap)+" byte")
    val blockIndex = sc.broadcast(blockIndexMap)

    val gt = sc.broadcast(newGT)

    val profileBlocksIndex = sc.broadcast(
      profileBlocksFiltered.map(pb => (pb.profileID, pb.blocks.size)).collectAsMap()
    )


    val numElements = blocksAfterFiltering.map(_.getAllProfiles.size).sum()
    val CNPThreshold = Math.floor((numElements/numProfiles)-1).toInt

    println("CNP Threshold "+CNPThreshold)


    val edgesAndCount = CNPFor.CNP(profileBlocksFiltered, blockIndex, maxProfileID.toInt, separatorID, gt, CNPThreshold, weightType, profileBlocksIndex)
    //val edgesAndCount = WNPFor.WNPJS(profileBlocksFiltered, blockIndex, profileBlocksIndex, maxProfileID.toInt, separatorID, gt, thresholdType)
    //val edgesAndCount = WNPFor.WNP(profileBlocksFiltered, blockIndex, maxProfileID.toInt, separatorID, gt, blocksEntropiesMap, thresholdType, weightType, profileBlocksIndex)
    //val edgesAndCount = WNPFor.CalcPCPQ(profileBlocksFiltered, blockIndex, maxProfileID.toInt, separatorID, gt)
    edgesAndCount.cache()
    val numEdges = edgesAndCount.map(_._1).sum()
    val edges = edgesAndCount.flatMap(_._2).distinct()
    edges.cache()
    val perfectMatch = edges.count()
    val pruningTime = Calendar.getInstance()
    blocksAfterFiltering.unpersist()
    blockIndex.unpersist()
    profileBlocksFiltered.unpersist()
    println("Number of retained edges "+numEdges)
    println("Number of perfect match found "+perfectMatch)
    println("Number of elements in the gt "+gtNum)
    println("PC = "+(perfectMatch.toFloat/gtNum.toFloat))
    println("PQ = "+(perfectMatch.toFloat/numEdges.toFloat))
    println()
    println("Time to pruning edges "+(pruningTime.getTimeInMillis-gtTime.getTimeInMillis)+" ms")
    println()
    println("Total execution time "+(pruningTime.getTimeInMillis-startTime.getTimeInMillis))
    sc.stop()
  }
}
