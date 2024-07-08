package peersim.kademlia.das;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

// Helper class for the ratedList
public class RatedListMember {

  // The maximum number of children one node can have. Also the maximum number of nodes that can be
  // returned from one sampling operation
  public static int maxNodesOnOneLevel = KademliaCommonConfigDas.MAX_NODES_RETURNED;

  // The maximum number of layers that can be in the ratedList
  public static int maxLevel = KademliaCommonConfigDas.MAX_RATED_LEVEL;

  public Set<RatedListMember> parents;
  public Set<RatedListMember> children;
  public BigInteger nodeID;
  public Integer level;
  public Integer rating;

  // The change in rating from a successful sampling operaiton
  private static Double successfulSampleChange =
      KademliaCommonConfigDas.RATED_SUCCESSFUL_SAMPLE_CHANGE;
  private Double failedSampleChange;

  // The maximum possible rating
  private static Double maxRating = KademliaCommonConfigDas.MAX_RATING;

  // The maximum number of tolerated consecutive failures
  private static int maxAcceptedFailures = KademliaCommonConfigDas.MAX_ACCEPTED_FAILURES;

  // The base for the exponential rating decrease
  private static int base = 3;
  private NodeStatus activity;
  private Double ratedScore;
  private Integer consecutiveFailures;

  private boolean parentalChange = false;
  private Set<RatedListMember> knownParentList;

  public RatedListMember(BigInteger nodeID, Integer level, Double RatedScore) {
    this.parents = new HashSet<>();
    this.children = new HashSet<>();
    this.knownParentList = new HashSet<>();
    this.nodeID = nodeID;
    this.level = level;
    this.activity = NodeStatus.INACTIVE;
    this.ratedScore = RatedScore;
    this.consecutiveFailures = 0;
    this.failedSampleChange = calculateFailedSampleChange(maxRating, maxAcceptedFailures);
  }

  public Boolean addParent(RatedListMember newParent) {

    if (newParent.level >= level) {
      // System.out.println("Tried to add a parent on the same or higher level. Reject");
      return false;
    }

    parents.add(newParent);
    parentalChange = true;
    return true;
  }

  public Boolean addChild(RatedListMember newChild) {

    // The bottom layer is infinite.
    if (newChild.level == 0) {
      children.add(newChild);
      return true;
    }

    if (children.size() >= maxNodesOnOneLevel) {
      // System.out.println("Tried to add a child to a full list level. Reject");
      // System.out.println("Node ID: " + nodeID);
      // System.out.println("Level: " + level);
      return false;
    }

    if (newChild.level > maxLevel) {
      // System.out.println("Tried to add a child above max level. Reject");
      return false;
    }

    if (newChild.level < level) {
      // System.out.println("Tried to add a child to a lower level. Reject");
      return false;
    }

    children.add(newChild);
    return true;
  }

  public Set<RatedListMember> getAllChildren() {
    Set<RatedListMember> allChildren = new HashSet<>();
    Queue<RatedListMember> queue = new LinkedList<>();

    // Start with direct children
    queue.addAll(this.children);

    while (!queue.isEmpty()) {
      RatedListMember current = queue.poll();
      if (allChildren.add(current)) {
        // Add current node's children to the queue
        queue.addAll(current.children);
      }
    }
    return allChildren;
  }

  public Set<RatedListMember> getAllParents() {

    // Only recalculate the parental list if there has been a detected change in parents
    if (parentalChange) {

      Set<RatedListMember> allParents = new HashSet<>();
      Queue<RatedListMember> queue = new LinkedList<>();

      // Start with direct children
      queue.addAll(this.parents);

      while (!queue.isEmpty()) {
        RatedListMember current = queue.poll();
        if (allParents.add(current)) {
          // Add current node's children to the queue
          queue.addAll(current.parents);
        }
      }
      knownParentList = allParents;
    }

    parentalChange = false;
    return knownParentList;
  }

  public RatedListMember findNode(BigInteger searchNodeID) {
    // Use a queue for Breadth-first-search.
    Queue<RatedListMember> queue = new LinkedList<>();
    Set<RatedListMember> visited = new HashSet<>();

    // Start with the current node
    queue.add(this);

    while (!queue.isEmpty()) {
      RatedListMember current = queue.poll();
      if (current.nodeID.equals(searchNodeID)) {
        return current;
      }
      visited.add(current);
      for (RatedListMember child : current.children) {
        if (!visited.contains(child)) {
          queue.add(child);
        }
      }
    }

    // Node not found
    return null;
  }

  public enum NodeStatus {
    ACTIVE,
    INACTIVE,
    DEFUNCT
  }

  // Function to calculate the minimum failedSampleChange value where a reputation at maxReputation
  // will reach zero after maxAcceptedFailures failed samples.
  public static double calculateFailedSampleChange(Double maxReputation, int maxAcceptedFailures) {
    int denominator = (int) Math.pow(base, maxAcceptedFailures) - 1;
    double F0 = (double) maxReputation * (base - 1) / denominator;
    return F0;
  }

  // Function if a sample is successful
  public void successfulSample() {
    ratedScore += successfulSampleChange;
    if (ratedScore > maxRating) {
      ratedScore = maxRating;
    }
    activity = NodeStatus.ACTIVE;
    consecutiveFailures = 0;
  }

  // Exponential decrease. Will cause a node at Maximum reputation to go to 0 reputation after
  // MAX_FAILURES consectuive failures.
  public void failedSample() {
    consecutiveFailures += 1;
    double decrement = failedSampleChange * (Math.pow(base, consecutiveFailures));
    ratedScore = ratedScore - decrement;
    activity = NodeStatus.INACTIVE;
    if (ratedScore <= 0) {
      activity = NodeStatus.DEFUNCT;
    }
  }

  public Boolean isDefunct() {
    return this.activity == NodeStatus.DEFUNCT;
  }

  public Double getRating() {
    return ratedScore;
  }

  public void setDefunct() {
    ratedScore = 0.0;
    activity = NodeStatus.DEFUNCT;
  }
}
