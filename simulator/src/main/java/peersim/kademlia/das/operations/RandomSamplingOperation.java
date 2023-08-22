package peersim.kademlia.das.operations;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import peersim.kademlia.das.Block;
import peersim.kademlia.das.KademliaCommonConfigDas;
import peersim.kademlia.das.MissingNode;
import peersim.kademlia.das.Sample;
// import peersim.kademlia.das.SearchTable;
import peersim.kademlia.das.SearchTable;

/**
 * This class represents a random sampling operation that collects samples from random nodes
 *
 * @author Sergi Rene
 * @version 1.0
 */
public class RandomSamplingOperation extends SamplingOperation {

  protected Block currentBlock;
  /**
   * default constructor
   *
   * @param srcNode Id of the node to find
   * @param destNode Id of the node to find
   * @param timestamp Id of the node to find
   */
  public RandomSamplingOperation(
      BigInteger srcNode,
      BigInteger destNode,
      long timestamp,
      Block currentBlock,
      SearchTable searchTable,
      boolean isValidator,
      int numValidators,
      MissingNode callback) {
    super(srcNode, destNode, timestamp, currentBlock, isValidator, numValidators, callback);
    this.currentBlock = currentBlock;
    this.searchTable = searchTable;

    Sample[] randomSamples = currentBlock.getNRandomSamples(KademliaCommonConfigDas.N_SAMPLES);
    for (Sample rs : randomSamples) {
      FetchingSample s = new FetchingSample(rs.getIdByRow());
      samples.put(rs.getIdByRow(), s);
      samples.put(rs.getIdByColumn(), s);
    }
  }

  public boolean completed() {

    boolean completed = true;
    for (FetchingSample s : samples.values()) {
      if (!s.isDownloaded()) {
        completed = false;
        break;
      }
    }
    return completed;
  }

  public BigInteger[] doSampling() {

    List<BigInteger> nextNodes = new ArrayList<>();

    while (true) { // I can send a new find request

      // get an available neighbour
      BigInteger nextNode = getNeighbour();
      if (nextNode != null) {
        nextNodes.add(nextNode);
      } else {
        break;
      }
    }

    if (nextNodes.size() > 0) return nextNodes.toArray(new BigInteger[0]);
    else return new BigInteger[0];
  }

  public void elaborateResponse(Sample[] sam) {

    for (Sample s : sam) {
      if (samples.containsKey(s.getId()) || samples.containsKey(s.getIdByColumn())) {
        FetchingSample fs = samples.get(s.getId());
        if (!fs.isDownloaded()) {
          samplesCount++;
          fs.setDownloaded();
        }
      }
    }
    System.out.println("Samples received " + samplesCount);
  }

  public void elaborateResponse(Sample[] sam, BigInteger node) {
    this.available_requests--;
    if (this.available_requests == 0) nodes.clear();

    for (Sample s : sam) {
      if (samples.containsKey(s.getId()) || samples.containsKey(s.getIdByColumn())) {
        FetchingSample fs = samples.get(s.getId());
        if (!fs.isDownloaded()) {
          samplesCount++;
          fs.setDownloaded();
          fs.removeFetchingNode(nodes.get(node));
        }
      }
    }

    nodes.remove(node);
    System.out.println("Samples received " + samplesCount);

    // System.out.println("Samples received " + samples.size());
  }

  public Map<String, Object> toMap() {
    // System.out.println("Mapping");
    Map<String, Object> result = new HashMap<String, Object>();

    result.put("id", this.operationId);
    result.put("src", this.srcNode);
    result.put("type", "RandomSamplingOperation");
    result.put("messages", getMessagesString());
    result.put("num_messages", getMessages().size());
    result.put("start", this.timestamp);
    result.put("completion_time", this.stopTime);
    result.put("hops", this.nrHops);
    result.put("samples", this.samplesCount);
    result.put("block_id", this.currentBlock.getBlockId());
    if (isValidator) result.put("validator", "yes");
    else result.put("validator", "no");
    if (completed()) result.put("completed", "yes");
    else result.put("completed", "no");
    return result;
  }
}
