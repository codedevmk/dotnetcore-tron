package org.tron.core.net.message;

import java.util.List;
import org.tron.core.Sha256Hash;
import org.tron.protos.Protocal.Inventory;
import org.tron.protos.Protocal.Inventory.InventoryType;

public class BlockInventoryMessage extends InventoryMessage {

  public BlockInventoryMessage(byte[] packed) {
    super(packed);
  }

  public BlockInventoryMessage(Inventory inv) {
    super(inv);
  }

  public BlockInventoryMessage(List<Sha256Hash> hashList) {
    Inventory.Builder invBuilder = Inventory.newBuilder();

    for (Sha256Hash hash :
        hashList) {
      invBuilder.addIds(hash.getByteString());
    }
    invBuilder.setType(InventoryType.BLOCK);
    inv = invBuilder.build();
    unpacked = true;
  }

  @Override
  public String toString() {
    return super.toString();
  }

  @Override
  public MessageTypes getType() {
    return MessageTypes.BLOCK_INVENTORY;
  }


}
