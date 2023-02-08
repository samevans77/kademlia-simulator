package peersim.kademlia;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import peersim.core.CommonState;

/**
 * This class implements a kademlia k-bucket. Function for the management of the neighbours update
 * are also implemented
 *
 * @author Daniele Furlan, Maurizio Bonani
 * @version 1.0
 */
public class KBucket implements Cloneable {
  protected List<BigInteger> replacements;

  // k-bucket array
  protected TreeMap<BigInteger, Long> neighbours = null;

  // empty costructor
  public KBucket() {
    neighbours = new TreeMap<BigInteger, Long>();
    replacements = new ArrayList<BigInteger>();
  }

  // add a neighbour to this k-bucket
  public boolean addNeighbour(BigInteger node) {
    long time = CommonState.getTime();
    if (neighbours.size() < KademliaCommonConfig.K) { // k-bucket isn't full
      neighbours.put(node, time); // add neighbour to the tail of the list
      removeReplacement(node);
      return true;
    } else {
      addReplacement(node);
      return false;
    }
  }

  // remove a neighbour from this k-bucket
  public void removeNeighbour(BigInteger node) {
    neighbours.remove(node);
  }

  public void addReplacement(BigInteger node) {
    replacements.add(0, node);
  }

  public void removeReplacement(BigInteger node) {
    replacements.remove(node);
  }

  public Object clone() {
    KBucket dolly = new KBucket();
    for (BigInteger node : neighbours.keySet()) {
      dolly.neighbours.put(new BigInteger(node.toByteArray()), 0l);
    }
    return dolly;
  }

  public String toString() {
    String res = "{\n";

    for (BigInteger node : neighbours.keySet()) {
      res += node + "\n";
    }

    return res + "}";
  }
}
