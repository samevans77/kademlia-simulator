package peersim.kademlia.das.operations;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import peersim.kademlia.das.Block;
import peersim.kademlia.das.KademliaCommonConfigDas;
import peersim.kademlia.das.MissingNode;
import peersim.kademlia.das.RatedListMember;
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

  protected static final double DIVERSITY_WEIGHT = KademliaCommonConfigDas.DIVERSITY_WEIGHT;
  protected static final double RATING_WEIGHT = KademliaCommonConfigDas.RATING_WEIGHT;

  protected static final double MAX_RATING = KademliaCommonConfigDas.MAX_RATING;

  protected static final int MAX_PARENT_DEPTH = KademliaCommonConfigDas.MAX_PARENT_DEPTH;

  protected HashMap<BigInteger, Set<RatedListMember>> knownParents;

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
      // Clearing the known parents list.
      knownParents = new HashMap<>();
      System.out.println("Calculating nodesByDiversity for " + nodes.values().size() + " nodes.");
      List<NodeDiversity> nodesByDiversity = orderByDiversity(nodes.values(), searchTable);
      System.out.println("Calculating nodesByRating...");
      List<NodeRating> nodesByRating = orderByRating(nodes.values(), searchTable);
      // List<NodeRating> nodesByRating = orderByRating(nodes.values(), searchTable);

      // for (NodeRating nr : nodesByRating) {
      //   nodeList.add(nr.node);
      // }
      // System.out.println("LENGTH OF DIVERSITY LIST: " + nodesByDiversity.size());
      // System.out.println("LENGTH OF RATING LIST: " + nodesByRating.size());
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

  public List<NodeDiversity> orderByDiversity(
      Collection<Node> nodeCollection, SearchTable searchTable) {
    List<Node> nodes = new ArrayList<>(nodeCollection);
    List<NodeDiversity> nodeDiversities = new ArrayList<>();

    for (Node candidate : nodes) {
      double candidateDiversity = calculateDiversity(candidate, nodes, searchTable);
      nodeDiversities.add(new NodeDiversity(candidate, candidateDiversity));
    }
    return nodeDiversities;
  }

  private double calculateDiversity(Node candidate, List<Node> nodes, SearchTable searchTable) {
    Set<RatedListMember> candidateAncestors = getNodeParents(candidate.getId(), searchTable);
    double diversityScore = 0;

    // If the candidate has no parents, it has no diversity in parents.
    if (candidateAncestors.isEmpty()) {
      return 0;
    }

    for (Node node : nodes) {
      // Don't compare the candidate to itself.
      if (node == candidate) continue;
      Set<RatedListMember> nodeAncestors = getNodeParents(node.getId(), searchTable);
      diversityScore += jaccardDistance(candidateAncestors, nodeAncestors);
    }
    return diversityScore / (nodes.size() - 1);
    // Return the average of a candidate node's parental diversity against all other node's parents
    // return diversityScore // Could just be like this instead if we wanted highest absolute score
  }

  private Set<RatedListMember> getNodeParents(BigInteger nodeID, SearchTable searchTable) {
    if (!knownParents.containsKey(nodeID))
      knownParents.put(nodeID, searchTable.getRatedListMember(nodeID).getAllParents());
    return knownParents.get(nodeID);
  }

  private void clearKnownParents() {
    knownParents = new HashMap<>();
  }

  private double jaccardDistance(Set<RatedListMember> set1, Set<RatedListMember> set2) {
    Set<RatedListMember> intersection = new HashSet<>(set1);
    intersection.retainAll(set2);

    Set<RatedListMember> union = new HashSet<>(set1);
    union.addAll(set2);

    return 1.0
        - ((double) intersection.size()
            / union
                .size()); // Jaccard coefficient complement (1 - Js) (we want the dissimilarity of
    // the two sets, not the similarity)
  }

  private static class NodeDiversity {
    Node node;
    double diversityScore;

    NodeDiversity(Node node, double diversityScore) {
      this.node = node;
      this.diversityScore = diversityScore;
    }
  }

  public List<NodeRating> orderByRating(Collection<Node> nodeCollection, SearchTable searchTable) {
    List<Node> nodes = new ArrayList<>(nodeCollection);
    List<NodeRating> nodesByRating = new ArrayList<>();

    for (Node candidate : nodes) {
      Double candidateRating = searchTable.getRatedListMember(candidate.getId()).getRating();
      nodesByRating.add(new NodeRating(candidate, candidateRating));
    }

    nodesByRating.sort((node1, node2) -> Double.compare(node2.getRating(), node1.getRating()));
    return nodesByRating;
  }

  private static class NodeRating {
    Node node;
    Double rating;

    NodeRating(Node node, Double rating) {
      this.node = node;
      this.rating = rating;
    }

    public double getRating() {
      return rating;
    }
  }

  public static List<Node> combineLists(
      List<NodeDiversity> nodesByDiversity,
      List<NodeRating> nodesByRating,
      double diversityWeight,
      double ratingWeight) {

    Map<Node, Double> combinedScores = new HashMap<>();

    // Add diversity scores to the map
    for (NodeDiversity nodeDiversity : nodesByDiversity) {
      combinedScores.put(nodeDiversity.node, diversityWeight * nodeDiversity.diversityScore);
    }

    // Add rating scores to the map, ignoring defunct nodes (negative ratings)
    for (NodeRating nodeRating : nodesByRating) {
      if (nodeRating.rating >= 0) {
        double normalisedRating = (double) nodeRating.rating / MAX_RATING;
        combinedScores.merge(nodeRating.node, (ratingWeight * normalisedRating), Double::sum);
      }
    }

    // We will end up querying defunct nodes *eventually*, but they'll be at the bottom of the list.

    // Create a list from the map entries and sort it by the combined score in descending order
    List<Map.Entry<Node, Double>> sortedEntries = new ArrayList<>(combinedScores.entrySet());
    sortedEntries.sort((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()));

    // Extract the nodes in the sorted order
    List<Node> combinedList = new ArrayList<>();
    for (Map.Entry<Node, Double> entry : sortedEntries) {
      combinedList.add(entry.getKey());
    }

    return combinedList;
  }

  public abstract void elaborateResponse(Sample[] sam, BigInteger node);

  public abstract void elaborateResponse(Sample[] sam);

  public int samplesCount() {
    return samplesCount;
  }
}
