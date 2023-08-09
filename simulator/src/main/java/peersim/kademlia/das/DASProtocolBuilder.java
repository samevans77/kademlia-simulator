package peersim.kademlia.das;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import peersim.core.CommonState;
import peersim.core.Node;
import peersim.edsim.EDSimulator;
import peersim.kademlia.Message;
import peersim.kademlia.Util;

public class DASProtocolBuilder extends DASProtocol {

  protected static String prefix = null;

  public DASProtocolBuilder(String prefix) {
    super(prefix);
    DASProtocolBuilder.prefix = prefix;
    isBuilder = true;
    isValidator = false;
  }

  @Override
  protected void handleGetSample(Message m, int myPid) {
    /** Ignore sample request * */
    logger.warning("Builder handle get sample - return nothing " + this);
  }

  @Override
  protected void handleSeedSample(Message m, int myPid) {
    System.err.println("Builder should not receive seed sample");
    System.exit(-1);
  }

  @Override
  protected void handleInitNewBlock(Message m, int myPid) {
    super.handleInitNewBlock(m, myPid);
    logger.warning("Builder new block:" + currentBlock.getBlockId());
    while (currentBlock.hasNext()) {
      Sample s = currentBlock.next();
      kv.add(s.getIdByRow(), s);
      kv.add(s.getIdByColumn(), s);
    }
    currentBlock.initIterator();

    int samplesWithinRegion = 0; // samples that are within at least one node's region
    int samplesValidators = 0;
    int samplesNonValidators = 0;
    while (currentBlock.hasNext()) {
      Sample s = currentBlock.next();
      boolean inRegion = false;
      BigInteger radiusValidator =
          currentBlock.computeRegionRadius(
              KademliaCommonConfigDas.NUM_SAMPLE_COPIES_PER_PEER, validatorsList.length);
      BigInteger radiusNonValidator =
          currentBlock.computeRegionRadius(KademliaCommonConfigDas.NUM_SAMPLE_COPIES_PER_PEER);
      // while (!inRegion) {
      List<BigInteger> idsValidatorsRows =
          searchTable.getValidatorNodesbySample(s.getIdByRow(), radiusValidator);
      List<BigInteger> idsValidatorsColumns =
          searchTable.getValidatorNodesbySample(s.getIdByColumn(), radiusValidator);

      List<BigInteger> idsNonValidatorsRows =
          searchTable.getNonValidatorNodesbySample(s.getIdByRow(), radiusNonValidator);
      List<BigInteger> idsNonValidatorsColumns =
          searchTable.getNonValidatorNodesbySample(s.getIdByColumn(), radiusNonValidator);

      List<BigInteger> idsValidators = new ArrayList<>();
      idsValidators.addAll(idsValidatorsRows);
      idsValidators.addAll(idsValidatorsColumns);

      List<BigInteger> idsNonValidators = new ArrayList<>();
      idsNonValidators.addAll(idsNonValidatorsRows);
      idsNonValidators.addAll(idsNonValidatorsColumns);

      logger.warning("New sample " + s.getRow() + " " + s.getColumn() + " " + idsValidators.size());
      /*  + " "
      + +idsNonValidators.size());*/

      for (BigInteger id : idsValidators) {
        Node n = Util.nodeIdtoNode(id, kademliaId);
        DASProtocol dasProt = ((DASProtocol) (n.getDASProtocol()));
        if (dasProt.isBuilder()) continue;
        if (n.isUp()) {
          Sample[] samples = {s};
          Message msg = generateSeedSampleMessage(samples);
          msg.operationId = -1;
          msg.src = this.getKademliaProtocol().getKademliaNode();
          msg.dst = n.getKademliaProtocol().getKademliaNode();
          sendMessage(msg, id, dasProt.getDASProtocolID());
          samplesValidators++;
          if (inRegion == false) {
            samplesWithinRegion++;
            inRegion = true;
          }
        }
      }

      for (BigInteger id : idsNonValidators) {
        Node n = Util.nodeIdtoNode(id, kademliaId);
        DASProtocol dasProt = ((DASProtocol) (n.getDASProtocol()));
        if (dasProt.isBuilder()) continue;
        if (n.isUp()) {
          if (inRegion == false) {
            samplesWithinRegion++;
            inRegion = true;
          }
          samplesNonValidators++;

          if (!dasProt.isValidator()) {
            EDSimulator.add(2, generateNewSampleMessage(s.getId()), n, dasProt.getDASProtocolID());
          }
        }
      }
      if (!inRegion) {
        radiusValidator = radiusValidator.multiply(BigInteger.valueOf(2));
        // radiusNonValidator = radiusNonValidator.multiply(BigInteger.valueOf(2));
      }
      // }
      // searchTable.getV
      /*while (!inRegion) {
        for (int i = 0; i < Network.size(); i++) {
          Node n = Network.get(i);
          DASProtocol dasProt = ((DASProtocol) (n.getDASProtocol()));
          BigInteger id = dasProt.getKademliaId();
          if (dasProt.isBuilder()) continue;
          logger.info(
              "Sample "
                  + s.getIdByRow()
                  + " "
                  + s.getIdByColumn()
                  + " "
                  + radiusValidator
                  + " "
                  + radiusNonValidator
                  + " "
                  + id);

          if (n.isUp()
              && (s.isInRegionByRow(id, radiusValidator)
                  || s.isInRegionByColumn(id, radiusValidator))) {
            // EDSimulator.add(0, generateSeedSampleMessage(s), n, dasProt.getDASProtocolID());
            if (dasProt.isValidator()) {
              Sample[] samples = {s};
              Message msg = generateSeedSampleMessage(samples);
              msg.operationId = -1;
              msg.src = this.getKademliaProtocol().getKademliaNode();
              msg.dst = n.getKademliaProtocol().getKademliaNode();
              sendMessage(msg, id, dasProt.getDASProtocolID());
              if (inRegion == false) {
                samplesWithinRegion++;
                inRegion = true;
              }

            }
          }
          if (n.isUp()
              && (s.isInRegionByRow(id, radiusNonValidator)
                  || s.isInRegionByColumn(id, radiusNonValidator))) {
            // EDSimulator.add(0, generateSeedSampleMessage(s), n, dasProt.getDASProtocolID());
            if (inRegion == false) {
              samplesWithinRegion++;
              inRegion = true;
            }
            if (!dasProt.isValidator()) {
              EDSimulator.add(
                  2, generateNewSampleMessage(s.getId()), n, dasProt.getDASProtocolID());
            }
          }

          if (!inRegion) {
            radiusValidator = radiusValidator.multiply(BigInteger.valueOf(2));
            // radiusNonValidator = radiusNonValidator.multiply(BigInteger.valueOf(2));
          }
        }
      }*/
    }
    logger.warning(
        samplesWithinRegion
            + " samples out of "
            + currentBlock.getNumSamples()
            + " samples are within a node's region"
            + " "
            + samplesValidators
            + " "
            + samplesNonValidators);
  }

  @Override
  protected void handleInitGetSample(Message m, int myPid) {
    logger.warning("Init block evil node - getting samples " + this);
    // super.handleInitGetSample(m, myPid);
  }

  @Override
  protected void handleGetSampleResponse(Message m, int myPid) {
    logger.warning("Received sample builder node: do nothing");
  }

  // ______________________________________________________________________________________________
  /**
   * generates a GET message for a specific sample.
   *
   * @return Message
   */
  protected Message generateSeedSampleMessage(Sample[] s) {

    Message m = new Message(Message.MSG_SEED_SAMPLE, s);
    m.timestamp = CommonState.getTime();

    return m;
  }

  // ______________________________________________________________________________________________
  /**
   * generates a GET message for t1 key.
   *
   * @return Message
   */
  private Message generateNewSampleMessage(BigInteger s) {

    Message m = Message.makeInitGetSample(s);
    m.timestamp = CommonState.getTime();

    return m;
  }

  /**
   * Replicate this object by returning an identical copy.<br>
   * It is called by the initializer and do not fill any particular field.
   *
   * @return Object
   */
  public Object clone() {
    DASProtocolBuilder dolly = new DASProtocolBuilder(DASProtocolBuilder.prefix);
    return dolly;
  }
}
