package peersim.kademlia.das;

import java.math.BigInteger;
import peersim.core.Node;
import peersim.kademlia.Message;

public class DASProtocolNonValidator extends DASProtocol {

  protected static String prefix = null;

  public DASProtocolNonValidator(String prefix) {
    super(prefix);
    DASProtocolNonValidator.prefix = prefix;
    isValidator = false;
    isBuilder = false;
  }

  @Override
  protected void handleSeedSample(Message m, int myPid) {
    System.err.println("Non-validator should not receive seed sample");
    System.exit(-1);
  }

  @Override
  protected void handleInitGetSample(Message m, int myPid) {
    logger.warning("Init block non-validator node - getting samples " + this);
    // super.handleInitGetSample(m, myPid);
    BigInteger[] reqSamples = {(BigInteger) m.body};
    BigInteger radius =
        currentBlock.computeRegionRadius(KademliaCommonConfigDas.NUM_SAMPLE_COPIES_PER_PEER);
    for (BigInteger sample : reqSamples) {
      for (BigInteger id : searchTable.getNodesbySample(sample, radius)) {
        Message msg = generateGetSampleMessage(reqSamples);
        msg.operationId = -1;
        msg.src = this.kadProtocol.getKademliaNode();
        Node n = kadProtocol.nodeIdtoNode(id);
        msg.dst = n.getKademliaProtocol().getKademliaNode();
        sendMessage(msg, id, myPid);
      }
    }
  }

  @Override
  protected void handleInitNewBlock(Message m, int myPid) {
    super.handleInitNewBlock(m, myPid);
    logger.warning("Starting random sampling");
    startRandomSampling();
  }

  /**
   * Replicate this object by returning an identical copy.<br>
   * It is called by the initializer and do not fill any particular field.
   *
   * @return Object
   */
  public Object clone() {
    DASProtocolNonValidator dolly = new DASProtocolNonValidator(DASProtocolNonValidator.prefix);
    return dolly;
  }
}
