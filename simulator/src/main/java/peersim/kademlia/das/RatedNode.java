package peersim.kademlia.das;

import java.math.BigInteger;

// RatedNode class is used by the SearchTable to determine appropriate nodes for searches.
public class RatedNode {

  private static Integer successfulSampleChange =
      KademliaCommonConfigDas.RATED_SUCCESSFUL_SAMPLE_CHANGE;
  private static Integer failedSampleChange = KademliaCommonConfigDas.RATED_FAILED_SAMPLE_CHANGE;

  private BigInteger nodeID;
  private NodeStatus activity;
  private Integer ratedScore;

  public RatedNode(BigInteger nodeID, Integer RatedScore) {
    this.nodeID = nodeID;
    this.activity = NodeStatus.INACTIVE;
    this.ratedScore = RatedScore;
  }

  public enum NodeStatus {
    ACTIVE,
    INACTIVE,
    DEFUNCT
  }

  public void successfulSample() {
    ratedScore = ratedScore + successfulSampleChange;
    activity = NodeStatus.ACTIVE;
  }

  public void failedSample() {
    ratedScore = ratedScore + failedSampleChange;
    activity = NodeStatus.INACTIVE;
    if (ratedScore < 0) {
      activity = NodeStatus.DEFUNCT;
    }
  }

  public Boolean isDefunct() {
    return this.activity == NodeStatus.DEFUNCT;
  }

  public Integer getRating() {
    return ratedScore;
  }
}
