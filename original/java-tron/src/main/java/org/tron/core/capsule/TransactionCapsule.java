/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.tron.core.capsule;

import static org.tron.common.crypto.Hash.sha256;
import static org.tron.common.utils.Utils.getRandom;
import static org.tron.protos.Protocal.Transaction.TranscationType.Transfer;

import com.google.protobuf.ByteString;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tron.common.crypto.ECKey;
import org.tron.common.crypto.ECKey.ECDSASignature;
import org.tron.common.utils.ByteArray;
import org.tron.core.Blockchain;
import org.tron.core.SpendableOutputs;
import org.tron.core.UTXOSet;
import org.tron.core.Wallet;
import org.tron.core.db.UtxoStore;
import org.tron.protos.Protocal.TXInput;
import org.tron.protos.Protocal.TXOutput;
import org.tron.protos.Protocal.Transaction;

public class TransactionCapsule {

  private static final Logger logger = LoggerFactory.getLogger("Transaction");
  private static final int RESERVE_BALANCE = 10;

  private Transaction transaction;

  /**
   * constructor TransactionCapsule.
   */
  public TransactionCapsule(Transaction trx) {
    this.transaction = trx;
  }

  /**
   * constructor TransactionCapsule.
   */
  public TransactionCapsule(byte[] address, String to, long amount, long balance, UtxoStore utxoStore) {

    Transaction.Builder transactionBuilder = Transaction.newBuilder().setType(Transfer);
    List<TXInput> txInputs = new ArrayList<>();
    List<TXOutput> txOutputs = new ArrayList<>();
    long spendableOutputs = balance;

    Set<Entry<String, long[]>> entrySet = utxoStore.findSpendableOutputs(address, amount)
        .getUnspentOutputs().entrySet();
    for (Map.Entry<String, long[]> entry : entrySet) {
      String txId = entry.getKey();
      long[] outs = entry.getValue();
      for (long out : outs) {
        TXInput txInput = TxInputCapsule
            .newTxInput(ByteArray.fromHexString(txId), out, null, address);
        txInputs.add(txInput);
      }
    }

    txOutputs.add(TxOutputCapsule.newTxOutput(amount, to));
    txOutputs
        .add(
            TxOutputCapsule.newTxOutput(spendableOutputs - amount, ByteArray.toHexString(address)));

    if (checkBalance(address, to, amount, balance)) {
      for (TXInput txInput : txInputs) {
        transactionBuilder.addVin(txInput);
      }
      for (TXOutput txOutput : txOutputs) {
        transactionBuilder.addVout(txOutput);
      }
      logger.info("Transaction create succeeded！");
      transaction = transactionBuilder.build();
    } else {
      logger.error("Transaction create failed！");
      transaction = null;
    }

  }

  /**
   * cheack balance of the address.
   */
  public boolean checkBalance(byte[] address, String to, long amount, long Balance) {

    if (to.length() != 40) {
      logger.error("address invalid");
      return false;
    }

    if (amount <= 0) {
      logger.error("amount required a positive number");
      return false;
    }

    if (amount > Balance) {
      logger.error("don't have enough money");
      return false;
    }

    return true;
  }

  public Transaction getTransaction() {
    return transaction;
  }

  /**
   * validate.
   */
  public boolean validate() {
    return true;
  }

  /**
   * Create a new transaction.
   *
   * @param wallet From which wallet.
   * @param to String to sender's address.
   * @param amount Long transaction amount.
   */
  public static Transaction newTransaction(Wallet wallet, String to, long amount, UTXOSet utxoSet) {
    List<TXInput> txInputs = new ArrayList<>();
    List<TXOutput> txOutputs = new ArrayList<>();

    byte[] pubKeyHash = wallet.getEcKey().getPubKey();

    SpendableOutputs spendableOutputs = utxoSet.findSpendableOutputs(pubKeyHash, amount);

    if (spendableOutputs.getAmount() < amount) {
      logger.error("Not enough funds");
      return null;
    }

    Set<Map.Entry<String, long[]>> entrySet = spendableOutputs.getUnspentOutputs().entrySet();

    for (Map.Entry<String, long[]> entry : entrySet) {
      String txId = entry.getKey();
      long[] outs = entry.getValue();

      for (long out : outs) {
        TXInput txInput = TxInputCapsule
            .newTxInput(ByteArray.fromHexString(txId), out, new byte[0], pubKeyHash);
        txInputs.add(txInput);
      }
    }

    txOutputs.add(TxOutputCapsule.newTxOutput(amount, to));
    if (spendableOutputs.getAmount() > amount) {
      txOutputs.add(
          TxOutputCapsule.newTxOutput(spendableOutputs.getAmount() - amount,
              ByteArray.toHexString(wallet.getAddress())));
    }

    Transaction.Builder transactionBuilder = Transaction.newBuilder();
    for (TXInput txInput : txInputs) {
      transactionBuilder.addVin(txInput);
    }

    for (TXOutput txOutput : txOutputs) {
      transactionBuilder.addVout(txOutput);
    }

    Transaction transaction = transactionBuilder.build();

    transaction = utxoSet.getBlockchain().signTransaction(transaction, wallet.getEcKey());

    return transaction;
  }

  /**
   * New coin-base transaction.
   *
   * @param to String to sender's address.
   * @param data String transaction data.
   * @return {@link Transaction}
   */
  public static Transaction newCoinbaseTransaction(String to, String data, long subsidy) {
    if (data == null || data.equals("")) {
      byte[] randBytes = new byte[20];
      SecureRandom random = getRandom();
      random.nextBytes(randBytes);
      data = "" + ByteArray.toHexString(randBytes);
    }

    TXInput txi = TxInputCapsule.newTxInput(new byte[]{}, -1, new byte[]{},
        ByteArray.fromHexString(data));
    TXOutput txo = TxOutputCapsule.newTxOutput(RESERVE_BALANCE, to);

    Transaction.Builder coinbaseTransaction = Transaction.newBuilder()
        .addVin(txi)
        .addVout(txo);
    coinbaseTransaction.setId(ByteString.copyFrom(getHash(coinbaseTransaction.build())));

    return coinbaseTransaction.build();
  }

  /**
   * Obtain a data bytes after removing the id and SHA-256(data).
   *
   * @param transaction {@link Transaction} transaction.
   * @return byte[] the hash of the transaction's data bytes which have no id.
   */
  public static byte[] getHash(Transaction transaction) {
    Transaction.Builder tmp = transaction.toBuilder();
    tmp.clearId();

    return sha256(tmp.build().toByteArray());
  }

  /**
   * Get data print string of the transaction.
   *
   * @param transaction {@link Transaction} transaction.
   * @return String format string of the transaction.
   */
  public static String toPrintString(Transaction transaction) {
    if (transaction == null) {
      return "";
    }

    StringBuilder sb = new StringBuilder("\nTransaction {\n"
        + "\tid=" + ByteArray.toHexString(transaction.getId()
        .toByteArray()) + "\n"
        + "\tvin=[\n");

    for (int i = 0, vinCount = transaction.getVinCount(); i < vinCount; i++) {
      TXInput vin = transaction.getVin(i);

      sb.append("\t\t{\n" + "\t\t\ttxID=").append(ByteArray.toHexString(vin.getTxID()
          .toByteArray())).append("\n").append("\t\t\tvout=").append(vin.getVout()).append("\n")
          .append("\t\t\tsignature=").append(ByteArray.toHexString(vin
          .getSignature().toByteArray())).append("\n").append("\t\t\tpubKey=")
          .append(ByteArray.toHexString(vin.getPubKey()
              .toByteArray())).append("\n").append("\t\t}");

      if (i != vinCount - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append("\t],\n\tvout=[\n");

    for (int i = 0, voutCount = transaction.getVoutCount(); i < voutCount; i++) {
      TXOutput vout = transaction.getVout(i);
      sb.append("\t\t{\n" + "\t\t\tvalue=").append(vout.getValue()).append("\n")
          .append("\t\t\tpubKeyHash=").append(ByteArray
          .toHexString(vout.getPubKeyHash().toByteArray())).append("\n").append("\t\t}");

      if (i != voutCount - 1) {
        sb.append(",");
      }
      sb.append("\n");
    }

    sb.append("\t]\n}");

    return sb.toString();
  }

  /**
   * Determine whether the transaction is a coin-base transaction.
   *
   * @param transaction {@link Transaction} transaction.
   * @return boolean true for coinbase, false for not coinbase.
   */
  public static boolean isCoinbaseTransaction(Transaction transaction) {
    return transaction.getVinList().size() == 1 && transaction.getVin(0)
        .getTxID().size() == 0 && transaction.getVin(0).getVout() == -1;
  }

  private static boolean checkTxOutUnSpent(TXOutput prevOut) {
    return true;//todo :check prevOut is unspent
  }

  /**
   * checkBalance.
   */
  private static boolean checkBalance(long totalBalance, long totalSpent) {
    return totalBalance == totalSpent;
  }

  /**
   * 1. check hash 2. check double spent 3. check sign 4. check balance
   */
  public static boolean validTransaction(Transaction signedTransaction, Blockchain blockchain) {
    if (TransactionCapsule.isCoinbaseTransaction(signedTransaction)) {
      return true;
    }
    //1. check hash
    ByteString idByteS = signedTransaction.getId(); //hash
    byte[] hash = TransactionCapsule.getHash(signedTransaction);
    ByteString hashByteS = ByteString.copyFrom(hash);
    if (idByteS == null || !hashByteS.equals(idByteS)) {
      return false;
    }
    Transaction.Builder transactionBuilderSigned = signedTransaction.toBuilder();
    Transaction.Builder transactionBuilderBeforSign = signedTransaction.toBuilder();

    int inSize = signedTransaction.getVinCount();
    //Clear all vin's signature and pubKey.
    for (int i = 0; i < inSize; i++) {
      TXInput vin = transactionBuilderBeforSign.getVin(i);
      TXInput.Builder vinBuilder = vin.toBuilder();
      vinBuilder.clearSignature();
      vinBuilder.clearPubKey();
      vin = vinBuilder.build();
      transactionBuilderBeforSign.setVin(i, vin);
    }

    long totalBalance = 0;
    long totalSpent = 0;
    Transaction transactionBeforSign = transactionBuilderBeforSign.build();//No sign no pubkey
    for (int i = 0; i < inSize; i++) {
      transactionBuilderBeforSign = transactionBeforSign.toBuilder();
      TXInput vin = transactionBuilderBeforSign.getVin(i);
      TXInput.Builder vinBuilder = vin.toBuilder();
      ByteString pubKeyBs = signedTransaction.getVin(i).getPubKey();
      byte[] pubKeyByteA = pubKeyBs.toByteArray();
      ByteString lockSript = ByteString
          .copyFrom(ECKey.computeAddress(pubKeyByteA));
      if (blockchain != null) {
        //need check lockSript
        ByteString txId = vin.getTxID();
        int out = (int) (vin.getVout());
        Transaction prevTx = blockchain.findTransaction(txId).toBuilder().build();
        if (prevTx == null) {
          return false;
        }
        TXOutput prevOut = prevTx.getVout(out);
        if (prevOut == null) {
          return false;
        }
        ByteString pubKeyHash = prevOut.getPubKeyHash();
        if (pubKeyHash == null || !pubKeyHash.equals(lockSript)) {
          return false;
        }
        //2. check double spent
        if (!checkTxOutUnSpent(prevOut)) {
          return false;
        }
        totalBalance += prevOut.getValue();
      }

      vinBuilder.setPubKey(lockSript);
      transactionBuilderBeforSign.setVin(i, vinBuilder.build());
      hash = getHash(transactionBuilderBeforSign.build());
      byte[] r = new byte[32];
      byte[] s = new byte[32];

      ByteString signBs = signedTransaction.getVin(i).getSignature();
      byte[] signByteA = signBs.toByteArray();

      if (signByteA.length != 65) {
        return false;
      }
      System.arraycopy(signByteA, 0, r, 0, 32);
      System.arraycopy(signByteA, 32, s, 0, 32);
      byte revId = signByteA[64];
      ECDSASignature signature = ECDSASignature.fromComponents(r, s, revId);
      //3. check sign
      if (!ECKey.verify(hash, signature, pubKeyByteA)) {
        return false;
      }
    }

    int outSize = signedTransaction.getVoutCount();
    for (int i = 0; i < outSize; i++) {
      totalSpent += signedTransaction.getVout(i).getValue();
    }

    //4. check balance
    return blockchain == null || checkBalance(totalBalance, totalSpent);
  }

  /**
   * sign the transaction.
   */
  public static Transaction sign(Transaction transaction, ECKey myKey) {
    if (TransactionCapsule.isCoinbaseTransaction(transaction)) {
      return null;
    }
    ByteString lockSript = ByteString.copyFrom(myKey.getAddress());
    Transaction.Builder transactionBuilderSigned = transaction.toBuilder();
    for (int i = 0; i < transaction.getVinList().size(); i++) {
      Transaction.Builder transactionBuilderForSign = transaction.toBuilder();
      TXInput vin = transaction.getVin(i);
      TXInput.Builder vinBuilder = vin.toBuilder();
      vinBuilder.clearSignature();
      vinBuilder.setPubKey(lockSript);
      transactionBuilderForSign.setVin(i, vinBuilder.build());
      byte[] hash = TransactionCapsule.getHash(transactionBuilderForSign.build());
      ECDSASignature signature = myKey.sign(hash);
      byte[] signByteA = signature.toByteArray();

      vinBuilder.setPubKey(ByteString.copyFrom(myKey.getPubKey()));
      vinBuilder.setSignature(ByteString.copyFrom(signByteA));
      transactionBuilderSigned.setVin(i, vinBuilder.build());
    }
    byte[] hash = TransactionCapsule.getHash(transactionBuilderSigned.build());
    transactionBuilderSigned.setId(ByteString.copyFrom(hash));
    transaction = transactionBuilderSigned.build();
    return transaction;
  }

  /**
   * Get sender.
   */
  public static byte[] getSender(Transaction tx) {
    byte[] pubKey = tx.getVin(0).getPubKey().toByteArray();
    return ECKey.computeAddress(pubKey);
  }

}
