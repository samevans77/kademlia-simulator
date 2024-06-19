package peersim.kademlia.das;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Logger;
import peersim.core.Node;
import peersim.kademlia.KademliaProtocol;

// SearchTable class is used by nodes as a local store to index all peers in the network discovered
public class SearchTable {

  protected Logger logger;
  protected KademliaProtocol kadProtocol;

  // Used to send known nodes to other peers
  private HashMap<BigInteger, Neighbour> neighbours;

  // private HashMap<BigInteger, Integer> ratedList;
  private HashMap<BigInteger, RatedNode> ratedList;
  private static Integer maxParentDepth = KademliaCommonConfigDas.MAX_PARENT_DEPTH;

  private static Double initialRating = KademliaCommonConfigDas.INITIAL_RATING;

  // Used to identify the "parent" of any individual "child" added to the searchtable
  private HashMap<BigInteger, List<BigInteger>> parents;

  // Used for local sampling
  private TreeSet<BigInteger> nodesIndexed;
  private static TreeSet<BigInteger> validatorsIndexed = new TreeSet<>();
  private TreeSet<BigInteger> nonValidatorsIndexed;

  // Builder node
  private BigInteger builderAddress;

  private HashSet<BigInteger> blackList;
  private List<Node> evilNodes;
  private List<BigInteger> evilIds;
  private boolean onlyAddEvilNghbrs = false;

  public SearchTable() {
    this.nodesIndexed = new TreeSet<>();
    this.nonValidatorsIndexed = new TreeSet<>();
    this.blackList = new HashSet<>();
    this.neighbours = new HashMap<>();
    this.parents = new HashMap<>();
    this.ratedList = new HashMap<>();
  }

  /**
   * sets the kademliaprotocol instance can be used to run kad operations
   *
   * @param prot KademliaProtocol
   */
  public void setKademliaProtocol(KademliaProtocol prot) {
    this.kadProtocol = prot;
    this.logger = prot.getLogger();
  }

  public void addNeighbour(Neighbour neigh, BigInteger parentID) {
    if (this.onlyAddEvilNghbrs && !neigh.isEvil()) {
      return;
    }

    addParent(neigh.getId(), parentID);
    ratedList.putIfAbsent(neigh.getId(), new RatedNode(neigh.getId(), initialRating));
    // logger.warning("Adding parent to " + neigh.getId() + " parentID: " + parentID);

    if (neigh.getId().compareTo(builderAddress) != 0) {
      if (neighbours.get(neigh.getId()) == null) {
        neighbours.put(neigh.getId(), neigh);
        nodesIndexed.add(neigh.getId());
      } else {
        if (neighbours.get(neigh.getId()).getLastSeen() < neigh.getLastSeen())
          neighbours.get(neigh.getId()).updateLastSeen(neigh.getLastSeen());
      }
    }
  }

  public void setOnlyAddEvilNghbrs() {
    this.onlyAddEvilNghbrs = true;
  }

  public void addNodes(BigInteger[] nodes) {

    for (BigInteger id : nodes) {
      if (id.compareTo(builderAddress) != 0) {
        if (!blackList.contains(id)
            && !validatorsIndexed.contains(id)
            && !builderAddress.equals(id)) {
          ratedList.putIfAbsent(id, new RatedNode(id, initialRating));
          nonValidatorsIndexed.add(id);
        }
      }
    }
  }

  public void seenNeighbour(BigInteger id, Node n) {
    if (id.compareTo(builderAddress) != 0) {
      if (neighbours.get(id) != null) {
        neighbours.remove(id);
        nodesIndexed.remove(id);
      }
      nodesIndexed.add(id);
      neighbours.put(id, new Neighbour(id, n, n.getDASProtocol().isEvil()));
    }
  }

  public void addValidatorNodes(BigInteger[] nodes) {
    for (BigInteger id : nodes) {
      if (!blackList.contains(id) && id.compareTo(builderAddress) != 0) {
        validatorsIndexed.add(id);
        ratedList.putIfAbsent(id, new RatedNode(id, initialRating));
      }
    }
  }

  public void setBuilderAddress(BigInteger builderAddress) {
    this.builderAddress = builderAddress;
  }

  public void removeNode(BigInteger node) {
    this.nodesIndexed.remove(node);
    this.nonValidatorsIndexed.remove(node);
    this.neighbours.remove(node);
    validatorsIndexed.remove(node);
  }

  public TreeSet<BigInteger> nodesIndexed() {
    return nodesIndexed;
  }

  public TreeSet<BigInteger> getValidatorsIndexed() {
    return validatorsIndexed;
  }

  public List<BigInteger> getNodesbySample(BigInteger sampleId, BigInteger radius) {

    BigInteger bottom = sampleId.subtract(radius);
    if (radius.compareTo(sampleId) == 1) bottom = BigInteger.ZERO;

    BigInteger top = sampleId.add(radius);
    if (top.compareTo(Block.MAX_KEY) == 1) top = Block.MAX_KEY;

    Collection<BigInteger> subSet = nodesIndexed.subSet(bottom, true, top, true);
    return new ArrayList<BigInteger>(subSet);
  }

  public List<BigInteger> getValidatorNodesbySample(BigInteger sampleId, BigInteger radius) {

    BigInteger bottom = sampleId.subtract(radius);
    if (radius.compareTo(sampleId) == 1) bottom = BigInteger.ZERO;

    BigInteger top = sampleId.add(radius);
    if (top.compareTo(Block.MAX_KEY) == 1) top = Block.MAX_KEY;
    Collection<BigInteger> subSet = validatorsIndexed.subSet(bottom, true, top, true);
    return new ArrayList<BigInteger>(subSet);
  }

  public List<BigInteger> getNonValidatorNodesbySample(BigInteger sampleId, BigInteger radius) {

    BigInteger bottom = sampleId.subtract(radius);
    if (radius.compareTo(sampleId) == 1) bottom = BigInteger.ZERO;

    BigInteger top = sampleId.add(radius);
    if (top.compareTo(Block.MAX_KEY) == 1) top = Block.MAX_KEY;

    Collection<BigInteger> subSet = nonValidatorsIndexed.subSet(bottom, true, top, true);
    return new ArrayList<BigInteger>(subSet);
  }

  public List<BigInteger> getNodesbySample(Set<BigInteger> samples, BigInteger radius) {

    List<BigInteger> result = new ArrayList<>();

    for (BigInteger sample : samples) {
      result.addAll(getNodesbySample(sample, radius));
    }
    return result;
  }

  public void setEvil(List<Node> nodes) {
    this.evilNodes = nodes;
  }

  public boolean isEvil(BigInteger id) {
    if (evilIds.contains(id)) return true;
    else return false;
  }

  public void setEvilIds(List<BigInteger> ids) {
    this.evilIds = ids;
  }

  public Neighbour[] getNeighbours() {

    List<Neighbour> result = new ArrayList<>();
    List<Neighbour> neighs = new ArrayList<>();
    for (Neighbour n : neighbours.values()) {
      neighs.add(n);
    }
    Collections.sort(neighs);

    for (Neighbour neigh : neighs) {
      if (result.size() < KademliaCommonConfigDas.MAX_NODES_RETURNED) result.add(neigh);
      else break;
    }
    return result.toArray(new Neighbour[0]);
  }

  public Neighbour[] getNeighbours(BigInteger id, BigInteger radius) {

    List<BigInteger> nodes = getNodesbySample(id, radius);
    List<Neighbour> neighs = new ArrayList<>();
    List<Neighbour> result = new ArrayList<>();
    for (BigInteger n : nodes) {
      neighs.add(neighbours.get(n));
    }
    Collections.sort(neighs);

    for (Neighbour neigh : neighs) {
      if (result.size() < KademliaCommonConfigDas.MAX_NODES_RETURNED) result.add(neigh);
      else break;
    }
    return result.toArray(new Neighbour[0]);
  }

  public int getAllNeighboursCount() {
    return neighbours.size();
  }

  public int getValidatorsNeighboursCount() {
    int count = 0;
    for (Neighbour neigh : neighbours.values()) {
      if (neigh.getNode().getDASProtocol().isValidator()) count++;
    }
    return count;
  }

  public int getNonValidatorsNeighboursCount() {
    int count = 0;
    for (Neighbour neigh : neighbours.values()) {
      if (!neigh.getNode().getDASProtocol().isValidator()) count++;
    }
    return count;
  }

  public int getAllAliveNeighboursCount() {
    int count = 0;
    for (Neighbour neigh : neighbours.values()) {
      if (neigh.getNode().isUp()) count++;
    }
    return count;
  }

  public Neighbour[] getEvilNeighbours(int n) {

    List<Neighbour> result = new ArrayList<>();
    if (evilNodes != null) {
      Collections.shuffle(evilNodes);
      for (Node neigh : evilNodes) {
        if (result.size() < n)
          result.add(new Neighbour(neigh.getDASProtocol().getKademliaId(), neigh, true));
        else break;
      }
    }
    return result.toArray(new Neighbour[0]);
  }

  public int getMaliciousNeighboursCount() {
    int count = 0;
    for (Neighbour neigh : neighbours.values()) {
      if (neigh.isEvil()) count++;
    }
    return count;
  }

  public boolean isNeighbourKnown(Neighbour neighbour) {
    return neighbours.containsKey(neighbour.getId());
  }

  public void refresh() {

    List<Neighbour> toRemove = new ArrayList<>();
    for (Neighbour neigh : neighbours.values()) {
      if (neigh.expired()) {
        toRemove.add(neigh);
        nodesIndexed.remove(neigh.getId());
      }
    }
    for (Neighbour n : toRemove) neighbours.remove(n.getId());
  }

  // Get the current parents of a node, add the new parent and put this into the parents hashmap.
  // Potentially inefficient, speak with onur about this?
  public void addParent(BigInteger childID, BigInteger parentID) {
    List<BigInteger> currentParents = parents.getOrDefault(childID, new ArrayList<>());
    currentParents.add(parentID);
    parents.put(childID, currentParents);

    // Puts the parent in the ratedlist only if it doesn't already exist there
    ratedList.putIfAbsent(parentID, new RatedNode(parentID, initialRating));
  }

  /**
   * Get all of a nodes parents up to a certain depth
   *
   * @param targetID The node whose ancestors need to be found
   * @param maxDepth The depth to which the search should be performed, set to -1 for unlimited
   *     depth
   * @return An Set of all parents up to a certain depth of the targetID with no duplicates.
   */
  public Set<BigInteger> getParents(BigInteger targetID, int maxDepth) {

    // Create a set to avoid duplicate values
    Set<BigInteger> allParents = new HashSet<>();

    // Recursive function, on the targetID with maxdepth value as given in an argument.
    findAllParents(targetID, allParents, 0, maxDepth);

    // Return a list of the set
    return allParents;
  }

  // Recursive helper procedure to find all ancestors to a certain given depth.
  private void findAllParents(
      BigInteger targetID, Set<BigInteger> allParents, int currentDepth, int maxDepth) {

    // If the depth of parent search is met or exceeded, then stop. Ignore if maxDepth is -1
    if (maxDepth != -1) if (currentDepth >= maxDepth) return;

    // Collect current direct parents from this node
    List<BigInteger> directParents = parents.get(targetID);

    // As long as the parents aren't nothing, enumerate them and if we haven't seen them before, get
    // their parents (up to the given depth)
    if (directParents != null) {
      for (BigInteger parent : directParents) {
        if (allParents.add(parent)) {
          findAllParents(parent, allParents, currentDepth + 1, maxDepth);
        }
      }
    }
  }

  /**
   * Called when the node passed in argument fails to respond to a sample request
   *
   * @param failedNode The node ID of the node which failed to respond
   */
  public void failedSample(BigInteger failedNode) {
    Set<BigInteger> parents = getParents(failedNode, maxParentDepth);
    ratedList.get(failedNode).failedSample();
    for (BigInteger parent : parents) {
      ratedList.get(parent).failedSample();
    }
  }

  /**
   * Simply increase the ratedList score for each parent of the node who successfully returned a
   * sample
   *
   * @param successfulNode The node ID of the responding node
   */
  public void successfulSample(BigInteger successfulNode) {

    if (!ratedList.containsKey(successfulNode))
      ratedList.put(successfulNode, new RatedNode(successfulNode, initialRating));
    ratedList.get(successfulNode).successfulSample();

    Set<BigInteger> parents = getParents(successfulNode, maxParentDepth);
    for (BigInteger parent : parents) {
      ratedList.get(parent).successfulSample();
    }
  }

  // Potentially bad algorithm, returns a rated node no matter what - creating a new one if one of
  // the same ID doesn't already exist.
  public RatedNode getRatedNode(BigInteger nodeID) {
    if (!ratedList.containsKey(nodeID)) ratedList.put(nodeID, new RatedNode(nodeID, initialRating));
    return ratedList.get(nodeID);
  }

  public void checkNodesToPurge() {
    // First get all neighbours
    Neighbour[] neighbourlist = getNeighbours();
    for (Neighbour n : neighbourlist) {

      // Then for each neighbour, look at their parents
      Set<BigInteger> parents = getParents(n.getId(), maxParentDepth);
      Boolean remove = true;

      // For each parent, check if any are not defunct, if they are then don't remove the node
      // This also removes the node if it has no parents
      for (BigInteger parent : parents) {
        if (!getRatedNode(parent).isDefunct()) remove = false;
      }

      if (remove) {
        // getRatedNode(n). // Set as defunct
        removeNode(n.getId());
      }
    }
  }
}
