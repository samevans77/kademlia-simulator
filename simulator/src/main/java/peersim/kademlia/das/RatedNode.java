package peersim.kademlia.das;

import java.math.BigInteger;

// RatedNode class is used by the SearchTable to determine appropriate nodes for searches.
public class RatedNode {

  private static Double successfulSampleChange =
      KademliaCommonConfigDas.RATED_SUCCESSFUL_SAMPLE_CHANGE;
  private Double failedSampleChange;
  private static Double maxRating = KademliaCommonConfigDas.MAX_RATING;
  private static int maxAcceptedFailures = KademliaCommonConfigDas.MAX_ACCEPTED_FAILURES;
  private static int base = 3;

  private BigInteger nodeID;
  private NodeStatus activity;
  private Double ratedScore;
  private Integer consecutiveFailures;

  public RatedNode(BigInteger nodeID, Double RatedScore) {
    this.nodeID = nodeID;
    this.activity = NodeStatus.INACTIVE;
    this.ratedScore = RatedScore;
    this.consecutiveFailures = 0;
    this.failedSampleChange = calculateFailedSampleChange(maxRating, maxAcceptedFailures);
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
}
