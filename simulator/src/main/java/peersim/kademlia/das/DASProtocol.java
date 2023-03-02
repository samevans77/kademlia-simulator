package peersim.kademlia.das;

/**
 * A Kademlia implementation for PeerSim extending the EDProtocol class.<br>
 * See the Kademlia bibliografy for more information about the protocol.
 *
 * @author Daniele Furlan, Maurizio Bonani
 * @version 1.0
 */
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.logging.Logger;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.kademlia.KademliaCommonConfig;
import peersim.kademlia.KademliaEvents;
import peersim.kademlia.KademliaProtocol;
import peersim.kademlia.KeyValueStore;
import peersim.kademlia.Message;
import peersim.kademlia.SimpleEvent;
import peersim.kademlia.das.operations.RandomSamplingOperation;
import peersim.transport.UnreliableTransport;

public class DASProtocol implements Cloneable, EDProtocol, KademliaEvents {

  private static final String PAR_TRANSPORT = "transport";
  private static final String PAR_DASPROTOCOL = "dasprotocol";
  private static final String PAR_KADEMLIA = "kademlia";

  private static String prefix = null;
  private UnreliableTransport transport;
  private int tid;
  private int kademliaId;
  // private int kademliaId;

  private KademliaProtocol kadProtocol;
  /** allow to call the service initializer only once */
  private static boolean _ALREADY_INSTALLED = false;

  private Logger logger;

  private BigInteger builderAddress;

  private boolean isBuilder;

  private KeyValueStore kv;

  private SearchTable searchTable;

  private Block currentBlock;

  private LinkedHashMap<Long, RandomSamplingOperation> samplingOp;

  /**
   * Replicate this object by returning an identical copy.<br>
   * It is called by the initializer and do not fill any particular field.
   *
   * @return Object
   */
  public Object clone() {
    DASProtocol dolly = new DASProtocol(DASProtocol.prefix);
    return dolly;
  }

  /**
   * Used only by the initializer when creating the prototype. Every other instance call CLONE to
   * create the new object.
   *
   * @param prefix String
   */
  public DASProtocol(String prefix) {

    DASProtocol.prefix = prefix;
    _init();
    tid = Configuration.getPid(prefix + "." + PAR_TRANSPORT);
    // System.out.println("New DASProtocol");

    kademliaId = Configuration.getPid(prefix + "." + PAR_KADEMLIA);
    kv = new KeyValueStore();
    searchTable = new SearchTable();
    samplingOp = new LinkedHashMap<Long, RandomSamplingOperation>();
  }

  /**
   * This procedure is called only once and allow to inizialize the internal state of
   * KademliaProtocol. Every node shares the same configuration, so it is sufficient to call this
   * routine once.
   */
  private void _init() {
    // execute once
    if (_ALREADY_INSTALLED) return;

    _ALREADY_INSTALLED = true;
  }

  /**
   * manage the peersim receiving of the events
   *
   * @param myNode Node
   * @param myPid int
   * @param event Object
   */
  public void processEvent(Node myNode, int myPid, Object event) {

    // Parse message content Activate the correct event manager fot the particular event
    // this.protocolId = myPid;

    Message m;
    // logger.warning("Message received");

    SimpleEvent s = (SimpleEvent) event;
    if (s instanceof Message) {
      m = (Message) event;
      m.dst = this.getKademliaProtocol().getKademliaNode();
    }

    switch (((SimpleEvent) event).getType()) {
      case Message.MSG_INIT_NEW_BLOCK:
        m = (Message) event;
        handleInitNewBlock(m, myPid);
        break;
      case Message.MSG_INIT_GET_SAMPLE:
        m = (Message) event;
        handleInitGetSample(m, myPid);
        break;
      case Message.MSG_GET_SAMPLE:
        m = (Message) event;
        handleGetSample(m, myPid);
        break;
      case Message.MSG_GET_SAMPLE_RESPONSE:
        m = (Message) event;
        handleGetSampleResponse(m, myPid);
        break;
        /*case Message.MSG_GET_ANY_SAMPLE:
          m = (Message) event;
          handleGetAnySample(m, myPid);
          break;
        case Message.MSG_GET_ANY_SAMPLE_RESPONSE:
          m = (Message) event;
          handleGetAnySampleResponse(m, myPid);
          break;*/
    }
  }

  public void setKademliaProtocol(KademliaProtocol prot) {
    this.kadProtocol = prot;
    this.logger = prot.getLogger();
  }

  public KademliaProtocol getKademliaProtocol() {
    return kadProtocol;
  }

  public boolean isBuilder() {
    return this.isBuilder;
  }

  public void setBuilder(boolean isBuilder) {
    this.isBuilder = isBuilder;
  }

  public void setBuilderAddress(BigInteger address) {
    this.builderAddress = address;
  }

  public BigInteger getBuilderAddress() {
    return this.builderAddress;
  }

  /**
   * Start a topic query opearation.<br>
   *
   * @param m Message received (contains the node to find)
   * @param myPid the sender Pid
   */
  private void handleInitNewBlock(Message m, int myPid) {
    currentBlock = (Block) m.body;

    if (isBuilder()) {

      logger.info("Builder new block:" + currentBlock.getBlockId());
      while (currentBlock.hasNext()) {
        Sample s = currentBlock.next();
        kv.add(s.getId(), s);
      }
    } else {
      startRandomSampling(m, myPid);
    }
  }

  /**
   * Start a topic query opearation.<br>
   *
   * @param m Message received (contains the node to find)
   * @param myPid the sender Pid
   */
  private void handleInitGetSample(Message m, int myPid) {
    BigInteger[] sampleId = new BigInteger[1];
    sampleId[0] = (BigInteger) m.body;

    if (isBuilder()) return;

    logger.info("Getting sample from builder " + sampleId);
    Message msg = generateGetSampleMessage(sampleId);
    msg.src = this.getKademliaProtocol().getKademliaNode();
    msg.dst =
        this.getKademliaProtocol()
            .nodeIdtoNode(builderAddress)
            .getKademliaProtocol()
            .getKademliaNode();
    sendMessage(msg, builderAddress, myPid);
  }

  private void handleGetSample(Message m, int myPid) {
    // for (BigInteger neigh : neighbours) logger.warning("Neighbours " + neigh);
    // create a response message containing the neighbours (with the same id of the request)
    logger.warning("KV size " + kv.occupancy());
    BigInteger[] samples = (BigInteger[]) m.body;
    for (BigInteger id : samples)
      if (!isBuilder()) logger.warning("Get sample request " + id + " from:" + m.src.getId());

    List<Sample> s = new ArrayList<>();

    for (BigInteger id : samples) {
      Sample sample = (Sample) kv.get(id);
      if (sample != null) s.add(sample);
      else logger.warning("Sample not found");
    }

    logger.warning("Responding with " + s.size() + " samples");

    Message response = new Message(Message.MSG_GET_SAMPLE_RESPONSE, s.toArray(new Sample[0]));
    response.operationId = m.operationId;
    response.dst = m.src;
    response.src = this.kadProtocol.getKademliaNode();
    response.ackId = m.id; // set ACK number
    sendMessage(response, m.src.getId(), myPid);
  }

  private void handleGetSampleResponse(Message m, int myPid) {

    if (m.body == null) return;

    Sample[] samples = (Sample[]) m.body;
    for (Sample s : samples) {
      logger.warning("Received sample:" + s.getId());
      kv.add((BigInteger) s.getId(), s);
    }

    RandomSamplingOperation op = samplingOp.get(m.operationId);
    if (op != null) {
      op.elaborateResponse(samples);
      logger.warning("Continue operation " + op.getId() + " " + op.getAvailableRequests());

      while ((op.getAvailableRequests() > 0)) { // I can send a new find request

        // get an available neighbour
        BigInteger nextNode = op.getNeighbour();

        if (nextNode != null) {
          if (!op.completed()) {

            // create a new request to send to neighbour

            BigInteger[] reqSamples = op.getSamples(currentBlock, nextNode);
            Message msg = generateGetSampleMessage(reqSamples);
            msg.operationId = op.getId();
            msg.src = this.kadProtocol.getKademliaNode();

            msg.dst = kadProtocol.nodeIdtoNode(nextNode).getKademliaProtocol().getKademliaNode();
            if (nextNode.compareTo(builderAddress) == 0) {
              logger.warning("Error sending to builder");
              continue;
            }
            sendMessage(msg, nextNode, myPid);
            op.nrHops++;

            // send find request
            // sendMessage(request, neighbour, myPid);
          }
        } else if (op.getAvailableRequests() == KademliaCommonConfig.ALPHA) {
          // no new neighbour and no outstanding requests
          // search operation finished

        } else { // no neighbour available but exists oustanding request to wait
          return;
        }
      }
    }
  }

  /*private void handleGetAnySample(Message m, int myPid) {
    // for (BigInteger neigh : neighbours) logger.warning("Neighbours " + neigh);
    // create a response message containing the neighbours (with the same id of the request)

    Collection<Sample> samples = new ArrayList<Sample>();
    for (Object sample : kv.getAll()) {
      Sample s = (Sample) sample;
      if (currentBlock.getBlockId() == s.getBlockId()) samples.add(s);
    }

    Sample[] samplesArray = samples.toArray(new Sample[samples.size()]);

    Message response = new Message(Message.MSG_GET_ANY_SAMPLE_RESPONSE, samplesArray);
    response.operationId = m.operationId;
    response.dst = m.dst;
    response.src = this.kadProtocol.getKademliaNode();
    response.ackId = m.id; // set ACK number
    sendMessage(response, m.src.getId(), myPid);
  }

  private void handleGetAnySampleResponse(Message m, int myPid) {

    Sample[] sArray = (Sample[]) m.body;
    logger.warning("Received sample array:" + sArray.length);

    for (Sample s : sArray) kv.add((BigInteger) s.getId(), s);
  }*/

  /**
   * send a message with current transport layer and starting the timeout timer (wich is an event)
   * if the message is a request
   *
   * @param m the message to send
   * @param destId the Id of the destination node
   * @param myPid the sender Pid
   */
  private void sendMessage(Message m, BigInteger destId, int myPid) {

    // int destpid;
    assert m.src != null;
    assert m.dst != null;

    Node src = this.kadProtocol.getNode();
    Node dest = this.kadProtocol.nodeIdtoNode(destId);

    // destpid = dest.getKademliaProtocol().getProtocolID();

    transport = (UnreliableTransport) (Network.prototype).getProtocol(tid);
    transport.send(src, dest, m, myPid);
  }

  // ______________________________________________________________________________________________
  /**
   * generates a GET message for a specific sample.
   *
   * @return Message
   */
  private Message generateGetSampleMessage(BigInteger[] sampleId) {

    Message m = new Message(Message.MSG_GET_SAMPLE, sampleId);
    m.timestamp = CommonState.getTime();

    return m;
  }

  // ______________________________________________________________________________________________
  /**
   * generates a GET any sample message.
   *
   * @return Message
   *     <p>private Message generateGetAnySampleMessage() {
   *     <p>Message m = new Message(Message.MSG_GET_ANY_SAMPLE, null); m.timestamp =
   *     CommonState.getTime();
   *     <p>return m; }
   */
  public BigInteger getKademliaId() {
    return this.getKademliaProtocol().getKademliaNode().getId();
  }

  @Override
  public void nodesFound(BigInteger[] neighbours) {
    List<BigInteger> list = new ArrayList<>(Arrays.asList(neighbours));
    list.remove(builderAddress);
    searchTable.addNode(list.toArray(new BigInteger[0]));
  }

  private void startRandomSampling(Message m, int myPid) {

    RandomSamplingOperation op =
        new RandomSamplingOperation(this.getKademliaId(), null, searchTable, m.timestamp);
    samplingOp.put(op.getId(), op);

    op.setAvailableRequests(KademliaCommonConfig.ALPHA);

    // send ALPHA messages
    for (int i = 0; i < KademliaCommonConfig.ALPHA; i++) {
      BigInteger nextNode = op.getNeighbour();
      if (nextNode != null) {
        BigInteger[] samples = op.getSamples(currentBlock, nextNode);
        Message msg = generateGetSampleMessage(samples);
        msg.operationId = op.getId();
        msg.src = this.kadProtocol.getKademliaNode();

        msg.dst = kadProtocol.nodeIdtoNode(nextNode).getKademliaProtocol().getKademliaNode();
        if (nextNode.compareTo(builderAddress) == 0) {
          logger.warning("Error sending to builder");
          continue;
        }
        sendMessage(msg, nextNode, myPid);
        logger.warning(
            "Sending sample request to: " + nextNode + " " + samples.length + " samples");
        op.nrHops++;
      }
    }
  }
}
