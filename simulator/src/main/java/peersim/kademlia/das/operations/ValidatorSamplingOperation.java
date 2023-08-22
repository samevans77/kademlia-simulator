package peersim.kademlia.das.operations;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import peersim.kademlia.das.Block;
import peersim.kademlia.das.MissingNode;
import peersim.kademlia.das.Sample;
import peersim.kademlia.das.SearchTable;

/**
 * This class represents the validator sampling operation that collects row or columns of samples
 *
 * @author Sergi Rene
 * @version 1.0
 */
public class ValidatorSamplingOperation extends SamplingOperation {

  // private RoutingTable rou;
  protected int row, column;
  /**
   * default constructor
   *
   * @param srcNode Id of the node to find
   * @param destNode Id of the node to find
   * @param timestamp Id of the node to find
   */
  public ValidatorSamplingOperation(
      BigInteger srcNode,
      long timestamp,
      Block block,
      SearchTable searchTable,
      int row,
      int column,
      boolean isValidator,
      int numValidators,
      MissingNode callback) {
    super(srcNode, null, timestamp, block, isValidator, numValidators, callback);

    // System.out.println("Row " + row + " column " + column);
    assert (row == 0 || column == 0) : "Either row or column should be set";
    assert (!(row == 0 && column == 0)) : "Both row or column are set";

    this.row = row;
    this.column = column;
    if (row > 0) {
      for (BigInteger sample : block.getSamplesIdsByRow(row)) {
        samples.put(sample, new FetchingSample(sample));
      }
    } else if (column > 0) {
      for (BigInteger sample : block.getSamplesIdsByColumn(column)) {
        samples.put(sample, new FetchingSample(sample));
      }
    }
    this.searchTable = searchTable;
  }

  public void elaborateResponse(Sample[] sam) {

    for (Sample s : sam) {
      if (row > 0) {
        if (samples.containsKey(s.getId())) {
          FetchingSample fs = samples.get(s.getId());
          if (!fs.isDownloaded()) {
            fs.setDownloaded();
            samplesCount++;
          }
        }
      } else {
        if (samples.containsKey(s.getIdByColumn())) {
          FetchingSample fs = samples.get(s.getIdByColumn());
          if (!fs.isDownloaded()) {
            fs.setDownloaded();
            samplesCount++;
          }
        }
      }
    }
    // System.out.println("Row " + samplesCount + " " + samples.size());
    if (samplesCount >= samples.size() / 2) completed = true;
  }

  public void elaborateResponse(Sample[] sam, BigInteger n) {

    this.available_requests--;
    if (this.available_requests == 0) nodes.clear();
    for (Sample s : sam) {
      if (row > 0) {
        if (samples.containsKey(s.getId())) {
          FetchingSample fs = samples.get(s.getId());
          if (!fs.isDownloaded()) {
            fs.setDownloaded();
            fs.removeFetchingNode(nodes.get(n));
            samplesCount++;
          }
        }
      } else {
        if (samples.containsKey(s.getIdByColumn())) {
          FetchingSample fs = samples.get(s.getIdByColumn());
          if (!fs.isDownloaded()) {
            fs.setDownloaded();
            fs.removeFetchingNode(nodes.get(n));
            samplesCount++;
          }
        }
      }
    }
    // System.out.println("Row " + samplesCount + " " + samples.size());
    if (samplesCount >= samples.size() / 2) completed = true;

    nodes.remove(n);
  }

  public boolean completed() {

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

  public int getRow() {
    return row;
  }

  public int getColumn() {
    return column;
  }

  public Map<String, Object> toMap() {
    // System.out.println("Mapping");
    Map<String, Object> result = new HashMap<String, Object>();

    result.put("id", this.operationId);
    result.put("src", this.srcNode);
    result.put("type", "ValidatorSamplingOperation");
    result.put("messages", getMessagesString());
    result.put("num_messages", getMessages().size());
    result.put("start", this.timestamp);
    result.put("completion_time", this.stopTime);
    result.put("hops", this.nrHops);
    result.put("samples", this.samplesCount);
    result.put("row", this.row);
    result.put("column", this.column);
    result.put("block_id", this.currentBlock.getBlockId());
    if (isValidator) result.put("validator", "yes");
    else result.put("validator", "no");
    if (completed) result.put("completed", "yes");
    else result.put("completed", "no");
    return result;
  }
}
