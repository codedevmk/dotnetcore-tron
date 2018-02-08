package org.tron.core.net.node;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tron.common.overlay.node.GossipLocalNode;
import org.tron.common.utils.ExecutorLoop;
import org.tron.common.utils.SafeMessageMap;
import org.tron.core.Sha256Hash;
import org.tron.core.net.message.BlockInventoryMessage;
import org.tron.core.net.message.BlockMessage;
import org.tron.core.net.message.FetchInvDataMessage;
import org.tron.core.net.message.InventoryMessage;
import org.tron.core.net.message.Message;
import org.tron.core.net.message.MessageTypes;
import org.tron.core.net.message.SyncBlockChainMessage;
import org.tron.core.net.message.TransactionInventoryMessage;
import org.tron.core.net.message.TransactionMessage;
import org.tron.core.net.peer.PeerConnection;
import org.tron.core.net.peer.PeerConnectionDelegate;
import org.tron.protos.Protocal;
import org.tron.protos.Protocal.Inventory.InventoryType;

public class NodeImpl extends PeerConnectionDelegate implements Node {

  private List<Sha256Hash> trxToAdvertise = new ArrayList<>();

  private List<Sha256Hash> blockToAdvertise = new ArrayList<>();

  private static final Logger logger = LoggerFactory.getLogger("Node");

  SafeMessageMap messageMap = new SafeMessageMap();

  private HashMap<Sha256Hash, PeerConnection> syncMap = new HashMap<>();

  private HashMap<Sha256Hash, PeerConnection> fetchMap = new HashMap<>();

  private NodeDelegate del;

  private GossipLocalNode gossipNode;

  private boolean isAdvertiseCancle = true;

  //loop

  ExecutorLoop<SyncBlockChainMessage> loopSyncBlockChain;

  ExecutorLoop<FetchInvDataMessage> loopFetchBlocks;

  ExecutorLoop<InventoryMessage> loopAdvertiseInv;

  @Override
  public void onMessage(PeerConnection peer, Message msg) {
    logger.info("on message");
    switch (msg.getType()) {
      case BLOCK:
        onHandleBlockMessage(peer, (BlockMessage) msg);
        break;
      case TRX:
        onHandleTransactionMessage(peer, (TransactionMessage) msg);
        break;
      case SYNC_BLOCK_CHAIN:
        onHandleSyncBlockChainMessage(peer, (SyncBlockChainMessage) msg);
        break;
      case FETCH_INV_DATA:
        onHandleFetchDataMessage(peer, (FetchInvDataMessage) msg);
        break;
      case BLOCK_INVENTORY:
        onHandleBlockInventoryMessage(peer, (BlockInventoryMessage) msg);
        break;
      default:
        throw new IllegalArgumentException("No such message");
    }
  }

  @Override
  public Message getMessage(Sha256Hash msgId) {
    return null;
  }


  @Override
  public void setNodeDelegate(NodeDelegate nodeDel) {
    this.del = nodeDel;
  }

  /**
   * broadcast msg.
   *
   * @param msg msg to bradcast
   */
  public void broadcast(Message msg) {
    if (msg instanceof BlockMessage) {
      blockToAdvertise.add(msg.sha256Hash());
    }
    if (msg instanceof TransactionMessage) {
      trxToAdvertise.add(msg.sha256Hash());
    }
  }

  @Override
  public void listen() {
    gossipNode = GossipLocalNode.getInstance();
    gossipNode.setPeerDel(this);
    gossipNode.start();
  }

  @Override
  public void close() throws InterruptedException {
    gossipNode.stop();
    loopFetchBlocks.join();
    loopSyncBlockChain.join();
    loopFetchBlocks.join();
    loopAdvertiseInv.join();
    isAdvertiseCancle = false;
  }

  @Override
  public void connectToP2PNetWork() {

    //broadcast inv
    loopAdvertiseInv = new ExecutorLoop<>(2, 10, b -> {
      logger.info("loop advertise inv");
      for (PeerConnection peer :
          gossipNode.listPeer.values()) {
        if (!peer.needSyncFrom) {
          peer.sendMessage(b);
        }
      }
      return null;
    }, throwable -> logger.error("Unhandled exception: ", throwable));

    //fetch blocks
    loopFetchBlocks = new ExecutorLoop<>(2, 10, c -> {
      logger.info("loop fetch blocks");
      if (fetchMap.containsKey(c.sha256Hash())) {
        fetchMap.get(c.sha256Hash()).sendMessage(c);
      }
      return null;
    }, throwable -> logger.error("Unhandled exception: ", throwable));

    //sync block chain
    loopSyncBlockChain = new ExecutorLoop<>(2, 10, d -> {
      logger.info("loop sync block chain");
      if (syncMap.containsKey(d.sha256Hash())) {
        syncMap.get(d.sha256Hash()).sendMessage(d);
      }
      return null;
    }, throwable -> logger.error("Unhandled exception: ", throwable));

    Thread advertiseloop = new Thread(() -> {
      while (isAdvertiseCancle) {
        if (blockToAdvertise.isEmpty() && trxToAdvertise.isEmpty()) {
          try {
            Thread.sleep(1000);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
        if (!trxToAdvertise.isEmpty()) {
          synchronized (this.trxToAdvertise) {
            loopAdvertiseInv.push(new TransactionInventoryMessage(trxToAdvertise));
            trxToAdvertise.clear();
          }
        }
        if (!blockToAdvertise.isEmpty()) {
          synchronized (this.blockToAdvertise) {
            loopAdvertiseInv.push(new BlockInventoryMessage(blockToAdvertise));
            blockToAdvertise.clear();
          }
        }

      }
    });

    advertiseloop.start();


  }

  @Override
  public void syncFrom(Sha256Hash myHeadBlockHash) {
    List<Sha256Hash> hashList = del.getBlockChainSummary(myHeadBlockHash, 100);
    Protocal.Inventory.Builder invBuild = Protocal.Inventory.newBuilder();
    invBuild.setType(Protocal.Inventory.InventoryType.BLOCK);
    int i = 0;
    for (Sha256Hash hash :
        hashList) {
      invBuild.setIds(i++, hash.getByteString());
    }

    try {
      while (gossipNode.listPeer.values().size() <= 0) {
        //todo: a loop here to wait the peers to sync blocks.
        logger.info("other peer is nil, please wait ... ");
        Thread.sleep(10000L);
        //return;
      }
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    loopSyncBlockChain.push(new SyncBlockChainMessage(hashList));
  }

  private void broadcastBlk(BlockMessage blkMsg) {
    blockToAdvertise.add(blkMsg.sha256Hash());
  }

  private void broadcastTrx(TransactionMessage trxMsg) {
    trxToAdvertise.add(trxMsg.sha256Hash());
  }


  private void onHandleBlockMessage(PeerConnection peer, BlockMessage blkMsg) {
    logger.info("on handle block message");
    peer.lastBlockWeKnow = blkMsg.sha256Hash();
    del.handleBlock(blkMsg);
  }

  private void onHandleTransactionMessage(PeerConnection peer, TransactionMessage trxMsg) {
    logger.info("on handle transaction message");
    del.handleTransaction(trxMsg);
  }

  private void onHandleSyncBlockChainMessage(PeerConnection peer, SyncBlockChainMessage syncMsg) {
    logger.info("on handle sync block chain message");
    List<Sha256Hash> blockIds = del.getBlockHashes(syncMsg.getHashList());
    BlockInventoryMessage blkInvMsg = new BlockInventoryMessage(blockIds);
    peer.sendMessage(blkInvMsg);
  }

  private void onHandleFetchDataMessage(PeerConnection peer, FetchInvDataMessage fetchInvDataMsg) {
    logger.info("on handle fetch block message");
    Protocal.Inventory inv = fetchInvDataMsg.getInventory();
    MessageTypes type =
        inv.getType() == InventoryType.BLOCK ? MessageTypes.BLOCK : MessageTypes.TRX;

    for (ByteString hash : inv.getIdsList()) {
      peer.sendMessage(del.getData(Sha256Hash.wrap(hash.toByteArray()), type));
    }
  }

  private void onHandleBlockInventoryMessage(PeerConnection peer, BlockInventoryMessage msg) {
    logger.info("on handle block inventory message");
    List<Sha256Hash> blockIds = del.getBlockHashes(msg.getHashList());
    FetchInvDataMessage fetchMsg = new FetchInvDataMessage(blockIds, InventoryType.BLOCK);
    fetchMap.put(fetchMsg.sha256Hash(), peer);
    loopFetchBlocks.push(fetchMsg);
  }
}
