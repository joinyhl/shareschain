
package shareschain.blockchain;

import shareschain.Shareschain;
import shareschain.ShareschainException;
import shareschain.database.DBKey;
import shareschain.util.Filter;
import shareschain.util.Logger;
import org.json.simple.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

public abstract class UnconfirmedTransaction implements Transaction {

    static UnconfirmedTransaction load(ResultSet rs) throws SQLException {
        Chain chain = Chain.getChain(rs.getInt("chain_id"));
        try {
            return chain.newUnconfirmedTransaction(rs);
        } catch (ShareschainException.NotValidException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private final TransactionImpl transaction;
    private final DBKey dbKey;
    private final long arrivalTimestamp;
    private final long feePerByte;
    private volatile boolean isBundled;

    UnconfirmedTransaction(TransactionImpl transaction, long arrivalTimestamp, boolean isBundled) {
        this.transaction = transaction;
        this.arrivalTimestamp = arrivalTimestamp;
        this.feePerByte = transaction.getFee() / transaction.getFullSize();
        this.isBundled = isBundled;
        this.dbKey = TransactionProcessorImpl.getInstance().unconfirmedTransactionDBKeyFactory.newKey(transaction.getId());
    }

    UnconfirmedTransaction(TransactionImpl.BuilderImpl builder,  ResultSet rs) throws SQLException {
        try {
            this.transaction = builder.build();
            this.transaction.setHeight(rs.getInt("transaction_height"));
            this.arrivalTimestamp = rs.getLong("arrival_timestamp");
            this.feePerByte = rs.getLong("fee_per_byte");
            this.isBundled = rs.getBoolean("is_bundled");
            this.dbKey = TransactionProcessorImpl.getInstance().unconfirmedTransactionDBKeyFactory.newKey(transaction.getId());
        } catch (ShareschainException.ValidationException e) {
            throw new RuntimeException(e.toString(), e);
        }
    }

    void save(Connection con) throws SQLException {
        Logger.logInfoMessage("MERGE INTO unconfirmed_transaction (id, transaction_height, "
                + "fee, fee_per_byte, is_bundled, expiration, transaction_bytes, arrival_timestamp, chain_id, height) "
                + "KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
        try (PreparedStatement pstmt = con.prepareStatement("MERGE INTO unconfirmed_transaction (id, transaction_height, "
                + "fee, fee_per_byte, is_bundled, expiration, transaction_bytes, arrival_timestamp, chain_id, height) "
                + "KEY (id, height) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            int i = 0;
            pstmt.setLong(++i, transaction.getId());
            pstmt.setInt(++i, transaction.getHeight());
            pstmt.setLong(++i, transaction.getFee());
            pstmt.setLong(++i, feePerByte);
            pstmt.setBoolean(++i, isBundled);
            pstmt.setInt(++i, transaction.getExpiration());
            pstmt.setBytes(++i, transaction.prunableBytes());
            pstmt.setLong(++i, arrivalTimestamp);
            pstmt.setInt(++i, transaction.getChain().getId());
            pstmt.setInt(++i, Shareschain.getBlockchain().getHeight());
            pstmt.executeUpdate();
        }
    }

    public TransactionImpl getTransaction() {
        return transaction;
    }

    long getArrivalTimestamp() {
        return arrivalTimestamp;
    }

    public boolean isBundled() {
        return isBundled;
    }

    @Override
    public final boolean equals(Object o) {
        return o instanceof UnconfirmedTransaction && transaction.equals(((UnconfirmedTransaction)o).getTransaction());
    }

    @Override
    public final int hashCode() {
        return transaction.hashCode();
    }

    @Override
    public Chain getChain() {
        return transaction.getChain();
    }

    @Override
    public long getId() {
        return transaction.getId();
    }

    DBKey getDBKey() {
        return dbKey;
    }

    @Override
    public String getStringId() {
        return transaction.getStringId();
    }

    @Override
    public long getSenderId() {
        return transaction.getSenderId();
    }

    @Override
    public byte[] getSenderPublicKey() {
        return transaction.getSenderPublicKey();
    }

    @Override
    public long getRecipientId() {
        return transaction.getRecipientId();
    }

    @Override
    public int getHeight() {
        return transaction.getHeight();
    }

    @Override
    public long getBlockId() {
        return transaction.getBlockId();
    }

    @Override
    public Block getBlock() {
        return transaction.getBlock();
    }

    @Override
    public int getTimestamp() {
        return transaction.getTimestamp();
    }

    @Override
    public int getBlockTimestamp() {
        return transaction.getBlockTimestamp();
    }

    @Override
    public short getDeadline() {
        return transaction.getDeadline();
    }

    @Override
    public int getExpiration() {
        return transaction.getExpiration();
    }

    @Override
    public long getAmount() {
        return transaction.getAmount();
    }

    @Override
    public long getFee() {
        return transaction.getFee();
    }

    @Override
    public long getMinimumFeeKER() {
        return transaction.getMinimumFeeKER();
    }

    @Override
    public byte[] getSignature() {
        return transaction.getSignature();
    }

    @Override
    public TransactionType getType() {
        return transaction.getType();
    }

    @Override
    public Attachment getAttachment() {
        return transaction.getAttachment();
    }

    @Override
    public boolean verifySignature() {
        return transaction.verifySignature();
    }

    @Override
    public void validate() throws ShareschainException.ValidationException {
        if (TransactionProcessorImpl.getInstance().getUnconfirmedTransaction(transaction.getId()) != null
                || getChain().getTransactionHome().hasTransaction(transaction)) {
            throw new ShareschainException.ExistingTransactionException("Transaction already processed");
        }
        transaction.validate();
    }

    @Override
    public byte[] getBytes() {
        return transaction.getBytes();
    }

    @Override
    public byte[] getUnsignedBytes() {
        return transaction.getUnsignedBytes();
    }

    @Override
    public byte[] getPrunableBytes() {
        return transaction.getPrunableBytes();
    }

    @Override
    public JSONObject getJSONObject() {
        return transaction.getJSONObject();
    }

    @Override
    public JSONObject getPrunableAttachmentJSON() {
        return transaction.getPrunableAttachmentJSON();
    }

    @Override
    public byte getVersion() {
        return transaction.getVersion();
    }

    @Override
    public int getFullSize() {
        return transaction.getFullSize();
    }

    @Override
    public List<? extends Appendix> getAppendages() {
        return transaction.getAppendages();
    }

    @Override
    public List<? extends Appendix> getAppendages(boolean includeExpiredPrunable) {
        return transaction.getAppendages(includeExpiredPrunable);
    }

    @Override
    public List<? extends Appendix> getAppendages(Filter<Appendix> filter, boolean includeExpiredPrunable) {
        return transaction.getAppendages(filter, includeExpiredPrunable);
    }

    @Override
    public int getECBlockHeight() {
        return transaction.getECBlockHeight();
    }

    @Override
    public long getECBlockId() {
        return transaction.getECBlockId();
    }

    @Override
    public short getIndex() {
        return transaction.getIndex();
    }

    @Override
    public byte[] getFullHash() {
        return transaction.getFullHash();
    }

    public ChainTransactionId getReferencedTransactionId() {
        return null;
    }

}
