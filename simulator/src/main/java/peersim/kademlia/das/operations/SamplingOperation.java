package peersim.kademlia.das.operations;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import peersim.kademlia.das.Block;
import peersim.kademlia.das.KademliaCommonConfigDas;
import peersim.kademlia.das.MissingNode;
import peersim.kademlia.das.Sample;
import peersim.kademlia.das.SearchTable;
import peersim.kademlia.operations.FindOperation;

public abstract class SamplingOperation extends FindOperation {

  protected SearchTable searchTable;
  protected int samplesCount = 0;
  protected boolean completed;
  protected boolean isValidator;
  protected MissingNode callback;
  protected Block currentBlock;
  protected int aggressiveness;
  // protected HashSet<BigInteger> queried;

  protected int timesIncreased;
  protected BigInteger radiusValidator, radiusNonValidator;

  protected LinkedHashMap<BigInteger, Node> nodes;
  protected HashMap<BigInteger, FetchingSample> samples;

  protected List<BigInteger> askNodes;

  protected boolean securityActive;

  protected static String prefix = null;
  protected static final String PAR_SECURITY_ACTIVE = "securityActive";

  protected static final double DIVERSITY_WEIGHT = 0.5;
  protected static final double RATING_WEIGHT = 0.5;

  public SamplingOperation(
      BigInteger srcNode,
      BigInteger destNode,
      long timestamp,
      Block block,
      boolean isValidator,
      int numValidators) {
    super(srcNode, destNode, timestamp);
    completed = false;
    this.isValidator = isValidator;
    currentBlock = block;

    radiusNonValidator =
        currentBlock.computeRegionRadius(KademliaCommonConfigDas.NUM_SAMPLE_COPIES_PER_PEER);
    samples = new HashMap<>();
    nodes = new LinkedHashMap<>();
    this.available_requests = 0;
    aggressiveness = 0;
    askNodes = new ArrayList<>();
    timesIncreased = 0;
    securityActive = KademliaCommonConfigDas.SECURITY_ACTIVE;
    // securityActive = Configuration.getBoolean(prefix + "." + PAR_SECURITY_ACTIVE,
    // KademliaCommonConfigDas.SECURITY_ACTIVE);
  }

  public SamplingOperation(
      BigInteger srcNode,
      BigInteger destNode,
      long timestamp,
      Block block,
      boolean isValidator,
      int numValidators,
      MissingNode callback) {
    super(srcNode, destNode, timestamp);
    samples = new HashMap<>();
    nodes = new LinkedHashMap<>();
    completed = false;
    this.isValidator = isValidator;
    this.callback = callback;
    currentBlock = block;
    this.available_requests = 0;
    aggressiveness = 0;
    radiusValidator =
        currentBlock.computeRegionRadius(
            KademliaCommonConfigDas.NUM_SAMPLE_COPIES_PER_PEER, numValidators);
    radiusNonValidator =
        currentBlock.computeRegionRadius(KademliaCommonConfigDas.NUM_SAMPLE_COPIES_PER_PEER);
    askNodes = new ArrayList<>();
    timesIncreased = 0;
    securityActive = KademliaCommonConfigDas.SECURITY_ACTIVE;
    // securityActive = Configuration.getBoolean(prefix + "." + PAR_SECURITY_ACTIVE,
    // KademliaCommonConfigDas.SECURITY_ACTIVE);
    // queried = new HashSet<>();
    // TODO Auto-generated constructor stub
  }

  public abstract boolean completed();

  public BigInteger[] getSamples() {
    List<BigInteger> result = new ArrayList<>();

    for (FetchingSample sample : samples.values()) {
      if (!sample.isDownloaded()) result.add(sample.getId());
    }

    return result.toArray(new BigInteger[0]);
  }

  public BigInteger getRadiusValidator() {
    return radiusValidator;
  }

  public BigInteger getRadiusNonValidator() {
    return radiusNonValidator;
  }

  protected abstract void createNodes();

  public BigInteger[] doSampling(SearchTable searchTable) {

    aggressiveness += KademliaCommonConfigDas.aggressiveness_step;
    for (Node n : nodes.values()) n.setAgressiveness(aggressiveness);
    List<BigInteger> result = new ArrayList<>();

    List<Node> nodeList = new ArrayList<>();

    if (securityActive) {
      // SECURITY: ORDER NODES BY DIVERSITY, AND RATING - THEN COMBINE
      List<Node> nodesByDiversity = orderByDiversity(nodes.values(), searchTable);
      List<Node> nodesByRating = orderByRating(nodes.values(), searchTable);
      nodeList = combineLists(nodesByDiversity, nodesByRating, DIVERSITY_WEIGHT, RATING_WEIGHT);
    } else {
      nodeList = new ArrayList<>(nodes.values());
    }

    // for (Node n : nodes.values()) {
    for (Node n : nodeList) {
      /*System.out.println(
          this.srcNode + "] Querying node " + n.getId() + " " + +n.getScore() + " " + this.getId());
      for (FetchingSample fs : n.getSamples())
        System.out.println(
            this.srcNode + "] Sample " + fs.beingFetchedFrom.size() + " " + fs.isDownloaded());*/

      if (!n.isBeingAsked() && n.getScore() > 0) { // break;
        n.setBeingAsked(true);
        this.available_requests++;
        for (FetchingSample s : n.getSamples()) {
          s.addFetchingNode(n);
        }
        result.add(n.getId());
      }
    }

    return result.toArray(new BigInteger[0]);
  }

  public List<Node> orderByDiversity(Collection<Node> nodeCollection, SearchTable searchTable) {
    List<Node> nodes = new ArrayList<>(nodeCollection);
    List<NodeDiversity> nodeDiversities = new ArrayList<>();

    for (Node candidate : nodes) {
      double candidateDiversity = calculateDiversity(candidate, nodes, searchTable);
      nodeDiversities.add(new NodeDiversity(candidate, candidateDiversity));
    }

    // Sort nodes based on diversity score in descending order
    nodeDiversities.sort((nd1, nd2) -> Double.compare(nd2.diversityScore, nd1.diversityScore));

    // Extract and return the sorted list of nodes
    List<Node> sortedNodes = new ArrayList<>();
    for (NodeDiversity nd : nodeDiversities) {
      sortedNodes.add(nd.node);
    }
    return sortedNodes;
  }

  private double calculateDiversity(Node candidate, List<Node> nodes, SearchTable searchTable) {
    Set<BigInteger> candidateAncestors = searchTable.getParents(candidate.getId(), 3);
    double diversityScore = 0;

    for (Node node : nodes) {
      // Don't compare the candidate to itself.
      if (node == candidate) continue;
      Set<BigInteger> nodeAncestors = searchTable.getParents(node.getId(), 3);
      diversityScore += jaccardDistance(candidateAncestors, nodeAncestors);
    }
    return diversityScore / (nodes.size() - 1);
    // Return the average of a candidate node's parental diversity against all other node's parents
    // return diversityScore // Could just be like this instead if we wanted highest absolute score
  }

  private double jaccardDistance(Set<BigInteger> set1, Set<BigInteger> set2) {
    Set<BigInteger> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);

    Set<BigInteger> union = new HashSet<>(set1);
    union.addAll(set2);

    return 1.0
        - ((double) intersection.size() / union.size()); // Jaccard coefficient complement (1 - Js)
  }

  private static class NodeDiversity {
    Node node;
    double diversityScore;

    NodeDiversity(Node node, double diversityScore) {
      this.node = node;
      this.diversityScore = diversityScore;
    }
  }

  public List<Node> orderByRating(Collection<Node> nodeCollection, SearchTable searchTable) {
    List<Node> nodes = new ArrayList<>(nodeCollection);
    List<NodeRating> nodesByRating = new ArrayList<>();

    for (Node candidate : nodes) {
      Integer candidateRating = searchTable.getRatedNode(candidate.getId()).getRating();
      nodesByRating.add(new NodeRating(candidate, candidateRating));
    }

    nodesByRating.sort((nd1, nd2) -> Double.compare(nd1.rating, nd2.rating));

    List<Node> sortedNodes = new ArrayList<>();
    for (NodeRating nr : nodesByRating) {
      sortedNodes.add(nr.node);
    }
    return sortedNodes;
  }

  private static class NodeRating {
    Node node;
    Integer rating;

    NodeRating(Node node, Integer rating) {
      this.node = node;
      this.rating = rating;
    }
  }

  public List<Node> combineLists(
      List<Node> nodesByDiversity,
      List<Node> nodesByRating,
      double diversityWeight,
      double ratingWeight) {
    HashMap<Node, Integer> diversityRankMap = new HashMap<>();
    HashMap<Node, Integer> ratingRankMap = new HashMap<>();

    // Assign ranks based on position in the diversity list
    for (int i = 0; i < nodesByDiversity.size(); i++) {
      diversityRankMap.put(nodesByDiversity.get(i), i + 1); // rank starts from 1
    }

    // Assign ranks based on position in the rating list
    for (int i = 0; i < nodesByRating.size(); i++) {
      ratingRankMap.put(nodesByRating.get(i), i + 1); // rank starts from 1
    }

    // Calculate combined score
    HashMap<Node, Double> combinedScoreMap = new HashMap<>();
    for (Node node : nodesByDiversity) {
      int diversityRank = diversityRankMap.get(node);
      int ratingRank = ratingRankMap.get(node);
      double combinedScore = diversityWeight * diversityRank + ratingWeight * ratingRank;
      combinedScoreMap.put(node, combinedScore);
    }

    // Create a list from the keys of the map and sort it by combined score
    List<Node> combinedList = new ArrayList<>(combinedScoreMap.keySet());
    combinedList.sort(Comparator.comparingDouble(combinedScoreMap::get));

    return combinedList;
  }

  public abstract void elaborateResponse(Sample[] sam, BigInteger node);

  public abstract void elaborateResponse(Sample[] sam);

  public int samplesCount() {
    return samplesCount;
  }
}
