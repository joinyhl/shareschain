package shareschain.node;

import shareschain.Shareschain;
import shareschain.ShareschainException.NotValidException;
import shareschain.permission.SecurityToken;
import shareschain.permission.SecurityTokenFactory;
import shareschain.blockchain.*;
import shareschain.blockchain.SmcTransaction;
import shareschain.util.Convert;
import shareschain.util.Logger;

import java.math.BigInteger;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NetworkMessage represents the messages exchanged between nodes.
 * <p>
 * Each network message has a common prefix followed by the message payload
 * <ul>
 * <li>Message name (string)
 * <li>Protocol level (short)
 * </ul>
 */
public abstract class NetworkMessage {

    /** Current protocol level - change this whenever a message format changes */
    private static final int PROTOCOL_LEVEL = 2;

    /** Maximum byte array length */
    public static final int MAX_ARRAY_LENGTH = 48 * 1024;

    /** Maximum list size */
    public static final int MAX_LIST_SIZE = 1500;

    /** UTF-8 character set */
    private static final Charset UTF8;
    static {
        try {
            UTF8 = Charset.forName("UTF-8");
        } catch (Exception exc) {
            Logger.logErrorMessage("Unable to create UTF-8 character set", exc);
            throw new ExceptionInInitializerError("Unable to create UTF-8 character set");
        }
    }

    /** Message identifier counter */
    private static final AtomicLong nextMessageId = new AtomicLong();

    /** Message request processor map */
    private static final Map<String, NetworkMessage> processors = new HashMap<>();
    static {
        processors.put("AddNodes", new AddNodesMessage());
        processors.put("BlockIds", new BlockIdsMessage());
        processors.put("BlockInventory", new BlockInventoryMessage());
        processors.put("BlockchainState", new BlockchainStateMessage());
        processors.put("Blocks", new BlocksMessage());
        processors.put("CumulativeDifficulty", new CumulativeDifficultyMessage());
        processors.put("Error", new ErrorMessage());
        processors.put("GetBlocks", new GetBlockMessage());
        processors.put("GetCumulativeDifficulty", new GetCumulativeDifficultyMessage());
        processors.put("GetInfo", new GetInfoMessage());
        processors.put("GetMilestoneBlockIds", new GetMilestoneBlockIdsMessage());
        processors.put("GetNextBlockIds", new GetNextBlockIdsMessage());
        processors.put("GetNextBlocks", new GetNextBlocksMessage());
        processors.put("GetNodes", new GetNodesMessage());
        processors.put("GetTransactions", new GetTransactionsMessage());
        processors.put("GetUnconfirmedTransactions", new GetUnconfirmedTransactionsMessage());
        processors.put("MilestoneBlockIds", new MilestoneBlockIdsMessage());
        processors.put("Transactions", new TransactionsMessage());
        processors.put("TransactionsInventory", new TransactionsInventoryMessage());
    }

    /** Message protocol level */
    private int protocolLevel;

    /** Message name bytes */
    private byte[] messageNameBytes;

    /** Message identifier */
    protected long messageId;

    /**
     * Create a new network message
     *
     * @param   messageName             Message name
     */
    private NetworkMessage(String messageName) {
        this.messageNameBytes = messageName.getBytes(UTF8);
        this.protocolLevel = PROTOCOL_LEVEL;
    }

    /**
     * Create a new network message
     *
     * @param   messageName                 Message name
     * @param   bytes                       Message bytes
     * @throws  BufferUnderflowException    Message is too short
     * @throws  NetworkException            Message is not valid
     */
    private NetworkMessage(String messageName, ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
        this.messageNameBytes = messageName.getBytes(UTF8);
        this.protocolLevel = (int)bytes.getShort() & 0xffff;
        if (this.protocolLevel != PROTOCOL_LEVEL) {
            throw new NetworkProtocolException("Protocol level " + this.protocolLevel + " is not accepted");
        }
    }

    /**
     * Get the network message from the message bytes
     *
     * @param   bytes                   Message bytes
     * @return                          Message
     * @throws  NetworkException        Message is not valid
     */
    static NetworkMessage getMessage(ByteBuffer bytes) throws NetworkException {
        NetworkMessage networkMessage;

        // 确定消息名称的长度是多少
        int length = (int)bytes.get() & 0xff;
        if (length < 1) {
            throw new NetworkException("Message name missing");
        }
        byte[] nameBytes = new byte[length];

        // 将字节数组从bytes拷贝到nameBytes，拷贝的长度是nameBytes的长度
        bytes.get(nameBytes);
        String messageName = new String(nameBytes);

        // 获取对应消息的消息处理器
        NetworkMessage processor = processors.get(messageName);
        try {
            if (processor != null) {
                // 取到处理器之后根据字节信息生成一个消息
                networkMessage = processor.constructMessage(bytes);
            } else {
                throw new NetworkException("'" + messageName + "' is not a valid node message");
            }
        } catch (BufferUnderflowException exc) {
            throw new NetworkException("'" + messageName + "' message is too short", exc);
        } catch (BufferOverflowException exc) {
            throw new NetworkException("'" + messageName + "' message buffer is too small", exc);
        }
        return networkMessage;
    }

    /**
     * Process the network message
     *
     * @param   node                    Node
     * @return                          Response message or null if this is a response
     */
    NetworkMessage processMessage(NodeImpl node) {
        return null;
    }

    /**
     * Construct the message
     *
     * @param   bytes                       Message bytes following the message name
     * @return                              Message
     * @throws  BufferOverflowException     Message buffer is too small
     * @throws  BufferUnderflowException    Message is too short
     * @throws  NetworkException            Message is not valid
     */
    protected NetworkMessage constructMessage(ByteBuffer bytes)
                                            throws BufferOverflowException, BufferUnderflowException, NetworkException {
        throw new RuntimeException("Required message processor missing");  // Should never happen
    }

    /**
     * Get the message length
     *
     * @return                          Message length
     */
    int getLength() {
        return 1 + messageNameBytes.length + 2;
    }

    /**
     * Get the network bytes
     *
     * @param   bytes                       Byte buffer
     * @throws  BufferOverflowException     Message buffer is too small
     */
    void getBytes(ByteBuffer bytes) throws BufferOverflowException {
        bytes.put((byte)messageNameBytes.length).put(messageNameBytes).putShort((short)protocolLevel);
    }

    /**
     * Get the message identifier
     *
     * @return                              Message identifier
     */
    long getMessageId() {
        return messageId;
    }

    /**
     * Get the message name
     *
     * @return                              Message name
     */
    String getMessageName() {
        return new String(messageNameBytes, UTF8);
    }

    /**
     * Check if the message requires a response
     *
     * @return                              TRUE if the message requires a response
     */
    boolean requiresResponse() {
        return false;
    }

    /**
     * Check if the message is a response
     *
     * @return                              TRUE if this is a response message
     */
    boolean isResponse() {
        return false;
    }

    /**
     * Check if blockchain download is not allowed
     *
     * @return                              TRUE if blockchain download is not allowed
     */
    boolean downloadNotAllowed() {
        return false;
    }

    /**
     * Check if light client should receive this message
     *
     * @return                              TRUE if light client should receive message
     */
    boolean sendToLightClient() {
        return false;
    }

    /**
     * Get the length of an encoded array
     *
     * @return                              Encoded array length
     */
    private static int getEncodedArrayLength(byte[] bytes) {
        int length = bytes.length;
        if (length < 254) {
            length++;
        } else if (length < 65536) {
            length += 3;
        } else {
            length += 5;
        }
        return length;
    }

    /**
     * Encode a byte array
     *
     * A byte array is encoded as a variable length field followed by the array bytes
     *
     * @param   bytes                       Byte buffer
     * @param   arrayBytes                  Array bytes
     * @throws  BufferOverflowException     Byte buffer is too small
     */
    private static void encodeArray(ByteBuffer bytes, byte[] arrayBytes) throws BufferOverflowException {
        if (arrayBytes.length > MAX_ARRAY_LENGTH) {
            throw new RuntimeException("Array length " + arrayBytes.length + " exceeds the maximum of " + MAX_ARRAY_LENGTH);
        }
        if (arrayBytes.length < 254) {
            bytes.put((byte)arrayBytes.length);
        } else if (arrayBytes.length < 65536) {
            bytes.put((byte)254).putShort((short)arrayBytes.length);
        } else {
            bytes.put((byte)255).putInt(arrayBytes.length);
        }
        if (arrayBytes.length > 0) {
            bytes.put(arrayBytes);
        }
    }

    /**
     * Decode a byte array
     *
     * A byte array is encoded as a variable length field followed by the array bytes
     *
     * @param   bytes                       Byte buffer
     * @return                              Array bytes
     * @throws  BufferUnderflowException    Message is too short
     * @throws  NetworkException            Message is not valid
     */
    private static byte[] decodeArray(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
        int length = (int)bytes.get() & 0xff;
        if (length == 254) {
            length = (int)bytes.getShort() & 0xffff;
        } else if (length == 255) {
            length = bytes.getInt();
        }
        if (length > MAX_ARRAY_LENGTH) {
            throw new NetworkException("Array length " + length + " exceeds the maximum of " + MAX_ARRAY_LENGTH);
        }
        byte[] arrayBytes = new byte[length];
        if (length > 0) {
            bytes.get(arrayBytes);
        }
        return arrayBytes;
    }

    /**
     * The GetInfo message is exchanged when a node connection is established.  There is no response message.
     * <ul>
     * <li>Application name (string)
     * <li>Application version (string)
     * <li>Application platform (string)
     * <li>Share address (boolean)
     * <li>Announced address (string)
     * <li>API port (short)
     * <li>SSL port (short)
     * <li>Available services (long)
     * <li>Disabled APIs (string)
     * <li>APIServer idle timeout (integer)
     * <li>Blockchain state (integer)
     * <li>Security token (variable length)
     * </ul>
     */
    public static class GetInfoMessage extends NetworkMessage {

        /** Authentication security token factory */
        private static final SecurityTokenFactory securityTokenFactory = SecurityTokenFactory.getSecurityTokenFactory();

        /** Application name */
        private final byte[] appNameBytes;

        /** Application platform */
        private final byte[] appPlatformBytes;

        /** Application version */
        private final byte[] appVersionBytes;

        /** Share address */
        private final boolean shareAddress;

        /** Announced address */
        private final byte[] announcedAddressBytes;

        /** API port */
        private final int apiPort;

        /** SSL port */
        private final int sslPort;

        /** Available services */
        private final long services;

        /** Disabled API (base64 encoded) */
        private final byte[] disabledAPIsBytes;

        /** APIServer idle timeout */
        private final int apiServerIdleTimeout;

        /** Authentication security token */
        private final SecurityToken securityToken;

        /** Blockchain state */
        private Node.BlockchainState blockchainState;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetInfoMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message or null
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetInfo.processRequest(node, this);
        }

        /**
         * Construct a GetInfo message
         */
        private GetInfoMessage() {
            super("GetInfo");
            this.appNameBytes = null;
            this.appVersionBytes = null;
            this.appPlatformBytes = null;
            this.shareAddress = false;
            this.announcedAddressBytes = null;
            this.apiPort = 0;
            this.sslPort = 0;
            this.services = 0;
            this.disabledAPIsBytes = null;
            this.apiServerIdleTimeout = 0;
            this.securityToken = null;
            this.blockchainState = Node.BlockchainState.UP_TO_DATE;
        }

        /**
         * Construct a GetInfo message
         *
         * @param   appName             Application name
         * @param   appVersion          Application version
         * @param   appPlatform         Application platform
         * @param   shareAddress        TRUE to share the network address with nodes
         * @param   announcedAddress    Announced address or null
         * @param   apiPort             API port
         * @param   sslPort             API SSL port
         * @param   services            Available application services
         * @param   disabledAPIs        Disabled API names
         * @param   apiServerIdleTimeout API server idle timeout
         * @param   nodePublicKey       Node public key or null
         */
        public GetInfoMessage(String appName, String appVersion, String appPlatform,
                              boolean shareAddress, String announcedAddress,
                              int apiPort, int sslPort, long services,
                              String disabledAPIs, int apiServerIdleTimeout,
                              byte[] nodePublicKey) {
            super("GetInfo");
            this.appNameBytes = appName.getBytes(UTF8);
            this.appVersionBytes = appVersion.getBytes(UTF8);
            this.appPlatformBytes = appPlatform.getBytes(UTF8);
            this.shareAddress = shareAddress;
            this.announcedAddressBytes = (announcedAddress != null ? announcedAddress.getBytes(UTF8) : Convert.EMPTY_BYTE);
            this.apiPort = apiPort;
            this.sslPort = sslPort;
            this.services = services;
            this.disabledAPIsBytes = (disabledAPIs != null ? disabledAPIs.getBytes(UTF8) : Convert.EMPTY_BYTE);
            this.apiServerIdleTimeout = apiServerIdleTimeout;
            this.blockchainState = Node.BlockchainState.UP_TO_DATE;
            if (nodePublicKey != null && securityTokenFactory != null) {
                this.securityToken = securityTokenFactory.getSecurityToken(nodePublicKey);
            } else {
                this.securityToken = null;
            }
        }

        /**
         * Construct a GetInfo message
         *
         * @param   bytes                       Message bytes following the message name
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        private GetInfoMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetInfo", bytes);
            this.appNameBytes = decodeArray(bytes);
            this.appVersionBytes = decodeArray(bytes);
            this.appPlatformBytes = decodeArray(bytes);
            this.shareAddress = (bytes.get() != 0);
            this.announcedAddressBytes = decodeArray(bytes);
            this.apiPort = (int)bytes.getShort() & 0xffff;
            this.sslPort = (int)bytes.getShort() & 0xffff;
            this.services = bytes.getLong();
            this.disabledAPIsBytes = decodeArray(bytes);
            this.apiServerIdleTimeout = bytes.getInt();
            int state = bytes.getInt();
            if (state < 0 || state >= Node.BlockchainState.values().length) {
                throw new NetworkException("Blockchain state '" + state + "' is not valid");
            }
            this.blockchainState = Node.BlockchainState.values()[state];
            if (bytes.hasRemaining()) {
                int length = bytes.getShort();
                if (length != 0) {
                    if (securityTokenFactory != null) {
                        this.securityToken = securityTokenFactory.getSecurityToken(bytes);
                    } else {
                        bytes.position(bytes.position() + length);
                        this.securityToken = null;
                    }
                } else {
                    this.securityToken = null;
                }
            } else {
                this.securityToken = null;
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength()
                    + getEncodedArrayLength(appNameBytes)
                    + getEncodedArrayLength(appVersionBytes)
                    + getEncodedArrayLength(appPlatformBytes)
                    + 1
                    + getEncodedArrayLength(announcedAddressBytes)
                    + 2 + 2 + 8
                    + getEncodedArrayLength(disabledAPIsBytes)
                    + 4
                    + 4
                    + 2 + (securityToken != null ? securityToken.getLength() : 0);
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                   Byte buffer
         * @throws  BufferOverflowException Message buffer is too small
         */
        @Override
        synchronized void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            encodeArray(bytes, appNameBytes);
            encodeArray(bytes, appVersionBytes);
            encodeArray(bytes, appPlatformBytes);
            bytes.put(shareAddress ? (byte)1 : (byte)0);
            encodeArray(bytes, announcedAddressBytes);
            bytes.putShort((short)apiPort).putShort((short)sslPort).putLong(services);
            encodeArray(bytes, disabledAPIsBytes);
            bytes.putInt(apiServerIdleTimeout);
            bytes.putInt(blockchainState.ordinal());
            if (securityToken != null) {
                bytes.putShort((short)securityToken.getLength());
                securityToken.getBytes(bytes);
            } else {
                bytes.putShort((short)0);
            }
        }

        /**
         * Get the application name
         *
         * @return                      Application name or null if no name specified
         */
        public String getApplicationName() {
            return (appNameBytes.length > 0 ? new String(appNameBytes, UTF8) : null);
        }

        /**
         * Get the application version
         *
         * @return                      Application version or null if no version specified
         */
        public String getApplicationVersion() {
            return (appVersionBytes.length > 0 ? new String(appVersionBytes, UTF8) : null);
        }

        /**
         * Get the application platform
         *
         * @return                      Application platform or null if no platform specified
         */
        public String getApplicationPlatform() {
            return (appPlatformBytes.length > 0 ? new String(appPlatformBytes, UTF8) : null);
        }

        /**
         * Check if the network address should be shared
         *
         * @return                      TRUE if the address should be shared
         */
        public boolean getShareAddress() {
            return shareAddress;
        }

        /**
         * Get the announced address
         *
         * @return                      Announced address or null if no address specified
         */
        public String getAnnouncedAddress() {
            return (announcedAddressBytes.length > 0 ? new String(announcedAddressBytes, UTF8) : null);
        }

        /**
         * Get the API port
         *
         * @return                      API port
         */
        public int getApiPort() {
            return apiPort;
        }

        /**
         * Get the SSL port
         *
         * @return                      SSL port
         */
        public int getSslPort() {
            return sslPort;
        }

        /**
         * Get the available services
         *
         * @return                      Service bits
         */
        public long getServices() {
            return services;
        }

        /**
         * Get the disabledAPIs
         *
         * @return                      disabledAPIs as base64 encoded string
         */
        public String getDisabledAPIs() {
            return (disabledAPIsBytes.length > 0 ? new String(disabledAPIsBytes, UTF8) : null);
        }

        /**
         * Get the API server idle timeout
         *
         * @return                      APIServer idle timeout
         */
        public int getApiServerIdleTimeout() {
            return apiServerIdleTimeout;
        }

        /**
         * Get the blockchain state
         *
         * @return                      Blockchain state
         */
        public synchronized Node.BlockchainState getBlockchainState() {
            return blockchainState;
        }

        /**
         * Set the blockchain state
         *
         * @param   blockchainState     Blockchain state
         */
        public synchronized void setBlockchainState(Node.BlockchainState blockchainState) {
            this.blockchainState = blockchainState;
        }

        /**
         * Get the permission security token
         *
         * @return                      Authentication security token or null
         */
        public SecurityToken getSecurityToken() {
            return securityToken;
        }
    }

    /**
     * The BlockchainState message is sent when blockchain state changes.
     * There is no response for this message.
     * <ul>
     * <li>Blockchain state (integer)
     * </ul>
     */
    public static class BlockchainStateMessage extends NetworkMessage {

        /** Blockchain state */
        private final Node.BlockchainState blockchainState;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new BlockchainStateMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return BlockchainState.processRequest(node, this);
        }

        /**
         * Construct a BlockchainState message
         */
        private BlockchainStateMessage() {
            super("BlockchainState");
            blockchainState = Node.BlockchainState.UP_TO_DATE;
        }

        /**
         * Construct a BlockchainState message
         *
         * @param   blockchainState             Blockchain state
         */
        public BlockchainStateMessage(Node.BlockchainState blockchainState) {
            super("BlockchainState");
            this.blockchainState = blockchainState;
        }

        /**
         * Construct a BlockchainState message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private BlockchainStateMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("BlockchainState", bytes);
            int state = bytes.getInt();
            if (state < 0 || state >= Node.BlockchainState.values().length) {
                throw new NetworkException("Blockchain state '" + state + "' is not valid");
            }
            this.blockchainState = Node.BlockchainState.values()[state];
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 4;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putInt(blockchainState.ordinal());
        }

        /**
         * Get the blockchain state
         *
         * @return                              Blockchain state
         */
        public Node.BlockchainState getBlockchainState() {
            return blockchainState;
        }

        /**
         * Check if light client should receive this message
         *
         * @return                              TRUE if light client should receive message
         */
        @Override
        boolean sendToLightClient() {
            return true;
        }
    }


    /**
     * The GetCumulativeDifficulty message is sent to a node to get the current blockchain status.  The
     * node responds with a CumulativeDifficulty message.
     * <ul>
     * <li>Message identifier (long)
     * </ul>
     */
    public static class GetCumulativeDifficultyMessage extends NetworkMessage {

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetCumulativeDifficultyMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message or null
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetCumulativeDifficulty.processRequest(node, this);
        }

        /**
         * Construct a GetCumulativeDifficulty message
         */
        public GetCumulativeDifficultyMessage() {
            super("GetCumulativeDifficulty");
            this.messageId = nextMessageId.incrementAndGet();
        }

        /**
         * Construct a GetCumulativeDifficulty message
         *
         * @param   bytes                       Message bytes following the message name
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetCumulativeDifficultyMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetCumulativeDifficulty", bytes);
            this.messageId = bytes.getLong();
        }

        /**
         * Get the message length
         *
         * @return                          Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                   Byte buffer
         * @throws  BufferOverflowException Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }
    }

    /**
     * The CumulativeDifficulty message is returned in response to the GetCumulativeDifficulty message.
     * The message identifier is obtained from the GetCumulativeDifficulty message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Cumulative difficulty (big integer)
     * <li>Block height (integer)
     * </ul>
     */
    public static class CumulativeDifficultyMessage extends NetworkMessage {

        /** Cumulative difficulty */
        private final byte[] cumulativeDifficultyBytes;

        /** Block height */
        private final int blockHeight;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new CumulativeDifficultyMessage(bytes);
        }

        /**
         * Construct a CumulativeDifficulty message
         */
        private CumulativeDifficultyMessage() {
            super("CumulativeDifficulty");
            this.messageId = 0;
            this.cumulativeDifficultyBytes = null;
            this.blockHeight = 0;
        }

        /**
         * Construct a CumulativeDifficulty message
         *
         * @param   messageId               Message identifier from the GetCumulativeDifficulty message
         * @param   cumulativeDifficulty    Cumulative difficulty
         * @param   blockHeight             Block height
         */
        public CumulativeDifficultyMessage(long messageId, BigInteger cumulativeDifficulty, int blockHeight) {
            super("CumulativeDifficulty");
            this.messageId = messageId;
            this.cumulativeDifficultyBytes = cumulativeDifficulty.toByteArray();
            this.blockHeight = blockHeight;
        }

        /**
         * Construct a CumulativeDifficulty message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private CumulativeDifficultyMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("CumulativeDifficulty", bytes);
            this.messageId = bytes.getLong();
            this.cumulativeDifficultyBytes = decodeArray(bytes);
            this.blockHeight = bytes.getInt();
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + getEncodedArrayLength(cumulativeDifficultyBytes) + 4;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            encodeArray(bytes, cumulativeDifficultyBytes);
            bytes.putInt(blockHeight);
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the cumulative difficulty
         *
         * @return                      Cumulative difficulty
         */
        public BigInteger getCumulativeDifficulty() {
            return new BigInteger(cumulativeDifficultyBytes);
        }

        /**
         * Get the block height
         *
         * @return                      Block height
         */
        public int getBlockHeight() {
            return blockHeight;
        }
    }

    /**
     * The GetNodes message is sent to a node to request a list of connected nodes.  The AddNodes
     * message is returned as an asynchronous response.
     */
    public static class GetNodesMessage extends NetworkMessage {
        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetNodesMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetNodes.processRequest(node, this);
        }

        /**
         * Construct a GetNodes message
         */
        public GetNodesMessage() {
            super("GetNodes");
        }

        /**
         * Construct a GetNodes message
         *
         * @param   bytes                       Message bytes following the message name
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetNodesMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetNodes", bytes);
        }

    }

    /**
     * The AddNodes message is sent to a node to update its node list and it is also returned
     * as an asynchronous response to the GetNodes message
     * <ul>
     * <li>Node list
     * </ul>
     * <p>
     * Each entry in the node list has the following format:
     * <ul>
     * <li>Announced address (string)
     * <li>Available services (long)
     * </ul>
     */
    public static class AddNodesMessage extends NetworkMessage {

        /** Announced addresses */
        private final List<byte[]> announcedAddressesBytes;

        /** Announced addresses length */
        private int announcedAddressesLength;

        /** Services */
        private final List<Long> services;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new AddNodesMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return AddNodes.processRequest(node, this);
        }

        /**
         * Construct an AddNodes message
         */
        private AddNodesMessage() {
            super("AddNodes");
            this.announcedAddressesBytes = null;
            this.announcedAddressesLength = 0;
            this.services = null;
        }

        /**
         * Construct an AddNodes message
         *
         * @param   nodeList                Node list
         */
        public AddNodesMessage(List<? extends Node> nodeList) {
            super("AddNodes");
            if (nodeList.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + nodeList.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            announcedAddressesBytes = new ArrayList<>(nodeList.size());
            announcedAddressesLength = 0;
            services = new ArrayList<>(nodeList.size());
            nodeList.forEach(node -> {
                String addr = node.getAnnouncedAddress();
                if (addr != null) {
                    byte[] addrBytes = addr.getBytes(UTF8);
                    announcedAddressesBytes.add(addrBytes);
                    announcedAddressesLength += getEncodedArrayLength(addrBytes);
                    services.add(((NodeImpl)node).getServices());
                }
            });
        }

        /**
         * Construct an AddNodes message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private AddNodesMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("AddNodes", bytes);
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + "exceeds the maximum of " + MAX_LIST_SIZE);
            }
            announcedAddressesBytes = new ArrayList<>(count);
            announcedAddressesLength = 0;
            services = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                byte[] addressBytes = decodeArray(bytes);
                announcedAddressesBytes.add(addressBytes);
                announcedAddressesLength += getEncodedArrayLength(addressBytes);
                services.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 2 + announcedAddressesLength + (8 * services.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putShort((short)announcedAddressesBytes.size());
            for (int i=0; i<announcedAddressesBytes.size(); i++) {
                encodeArray(bytes, announcedAddressesBytes.get(i));
                bytes.putLong(services.get(i));
            }
        }

        /**
         * Get the announced addresses
         *
         * @return                          Announced addresses
         */
        public List<String> getAnnouncedAddresses() {
            List<String> addresses = new ArrayList<>(announcedAddressesBytes.size());
            announcedAddressesBytes.forEach((addressBytes) -> addresses.add(new String(addressBytes, UTF8)));
            return addresses;
        }

        /**
         * Get the services
         *
         * @return                          Services
         */
        public List<Long> getServices() {
            return services;
        }
    }

    /**
     * The GetMilestoneBlockIds message is sent when a node is downloading the blockchain.
     * The MilestoneBlockIds message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Last block identifier (long)
     * <li>Last milestone block identifier (long)
     * </ul>
     */
    public static class GetMilestoneBlockIdsMessage extends NetworkMessage {

        /** Last block identifier */
        private final long lastBlockId;

        /** Last milestone block identifier */
        private final long lastMilestoneBlockId;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetMilestoneBlockIdsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetMilestoneBlockIds.processRequest(node, this);
        }

        /**
         * Construct a GetMilestoneBlockIds message
         */
        private GetMilestoneBlockIdsMessage() {
            super("GetMilestoneBlockIds");
            this.messageId = 0;
            this.lastBlockId = 0;
            this.lastMilestoneBlockId = 0;
        }

        /**
         * Construct a GetMilestoneBlockIds message
         *
         * @param   lastBlockId             Last block identifier or 0
         * @param   lastMilestoneBlockId    Last milestone block identifier or 0
         */
        public GetMilestoneBlockIdsMessage(long lastBlockId, long lastMilestoneBlockId) {
            super("GetMilestoneBlockIds");
            this.messageId = nextMessageId.incrementAndGet();
            this.lastBlockId = lastBlockId;
            this.lastMilestoneBlockId = lastMilestoneBlockId;
        }

        /**
         * Construct a GetMilestoneBlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetMilestoneBlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetMilestoneBlockIds", bytes);
            this.messageId = bytes.getLong();
            this.lastBlockId = bytes.getLong();
            this.lastMilestoneBlockId = bytes.getLong();
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 8;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId).putLong(lastBlockId).putLong(lastMilestoneBlockId);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the last block identifier
         *
         * @return                          Last block identifier or 0
         */
        public long getLastBlockId() {
            return lastBlockId;
        }

        /**
         * Get the last milestone block identifier
         *
         * @return                          Last milestone block identifier or 0
         */
        public long getLastMilestoneBlockIdentifier() {
            return lastMilestoneBlockId;
        }
    }

    /**
     * The MilestoneBlockIds message is returned in response to the GetMilestoneBlockIds message.
     * The message identifier is obtained from the GetMilestoneBlockIds message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Last block indicator (boolean)
     * <li>Block identifier list (long)
     * </ul>
     */
    public static class MilestoneBlockIdsMessage extends NetworkMessage {

        /** Last block indicator */
        private final boolean isLastBlock;

        /** Block identifiers */
        private final List<Long> blockIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new MilestoneBlockIdsMessage(bytes);
        }

        /**
         * Construct a MilestoneBlockIds message
         */
        private MilestoneBlockIdsMessage() {
            super("MilestoneBlockIds");
            this.messageId = 0;
            this.isLastBlock = false;
            this.blockIds = null;
        }

        /**
         * Construct a MilestoneBlockIds message
         *
         * @param   messageId               Message identifier
         * @param   isLastBlock             Last block indicator
         * @param   blockIds                Block identifier list
         */
        public MilestoneBlockIdsMessage(long messageId, boolean isLastBlock, List<Long> blockIds) {
            super("MilestoneBlockIds");
            if (blockIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blockIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            this.isLastBlock = isLastBlock;
            this.blockIds = blockIds;
        }

        /**
         * Construct a MilestoneBlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private MilestoneBlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("MilestoneBlockIds", bytes);
            this.messageId = bytes.getLong();
            this.isLastBlock = (bytes.get() != 0);
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            blockIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                blockIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 1 + 2 + (8 * blockIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.put(isLastBlock ? (byte)1 : (byte)0);
            bytes.putShort((short)blockIds.size());
            blockIds.forEach(bytes::putLong);
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the last block indicator
         *
         * @return                          Last block indicator
         */
        public boolean isLastBlock() {
            return isLastBlock;
        }

        /**
         * Get the milestone block identifiers
         *
         * @return                          Milestone block identifiers
         */
        public List<Long> getBlockIds() {
            return blockIds;
        }
    }

    /**
     * The GetNextBlockIds message is sent when a node is downloading the blockchain.
     * The BlockIds message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Start block identifier (long)
     * <li>Maximum number of blocks (integer)
     * </ul>
     */
    public static class GetNextBlockIdsMessage extends NetworkMessage {

        /** Start block identifier */
        private final long blockId;

        /** Maximum number of blocks */
        private final int limit;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetNextBlockIdsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetNextBlockIds.processRequest(node, this);
        }

        /**
         * Construct a GetNextBlockIds message
         */
        private GetNextBlockIdsMessage() {
            super("GetNextBlockIds");
            this.messageId = 0;
            this.blockId = 0;
            this.limit = 0;
        }

        /**
         * Construct a GetNextBlockIds message
         *
         * @param   blockId                 Start block identifier
         * @param   limit                   Maximum number of blocks
         */
        public GetNextBlockIdsMessage(long blockId, int limit) {
            super("GetNextBlockIds");
            this.messageId = nextMessageId.incrementAndGet();
            this.blockId = blockId;
            this.limit = limit;
        }

        /**
         * Construct a GetNextBlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetNextBlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetNextBlockIds", bytes);
            this.messageId = bytes.getLong();
            this.blockId = bytes.getLong();
            this.limit = bytes.getInt();
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 4;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId).putLong(blockId).putInt(limit);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the start block identifier
         *
         * @return                          Start block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Get the limit
         *
         * @return                          Limit
         */
        public int getLimit() {
            return limit;
        }
    }

    /**
     * The BlockIds message is returned in response to the GetNextBlockIds message.
     * The message identifier is obtained from the GetNextBlockIds message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Block identifier list (long)
     * </ul>
     */
    public static class BlockIdsMessage extends NetworkMessage {

        /** Block identifiers */
        private final List<Long> blockIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new BlockIdsMessage(bytes);
        }

        /**
         * Construct a BlockIds message
         */
        private BlockIdsMessage() {
            super("BlockIds");
            messageId = 0;
            blockIds = null;
        }

        /**
         * Construct a BlockIds message
         *
         * @param   messageId               Message identifier
         * @param   blockIds                Block identifier list
         */
        public BlockIdsMessage(long messageId, List<Long> blockIds) {
            super("BlockIds");
            if (blockIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blockIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            this.blockIds = blockIds;
        }

        /**
         * Construct a BlockIds message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private BlockIdsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("BlockIds", bytes);
            this.messageId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.blockIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                blockIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + (8 * blockIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)blockIds.size());
            blockIds.forEach(bytes::putLong);
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the block identifiers
         *
         * @return                          Block identifiers
         */
        public List<Long> getBlockIds() {
            return blockIds;
        }
    }

    /**
     * The GetNextBlocks message is sent when a node is downloading the blockchain.
     * The Blocks message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Start block identifier (long)
     * <li>Maximum number of blocks (integer)
     * <li>Block identifier list (long)
     * </ul>
     */
    public static class GetNextBlocksMessage extends NetworkMessage {

        /** Start block identifier */
        private final long blockId;

        /** Maximum number of blocks */
        private final int limit;

        /** Block identifier list */
        private final List<Long> blockIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetNextBlocksMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetNextBlocks.processRequest(node, this);
        }

        /**
         * Construct a GetNextBlocks message
         */
        private GetNextBlocksMessage() {
            super("GetNextBlocks");
            this.messageId = 0;
            this.blockId = 0;
            this.limit = 0;
            this.blockIds = null;
        }

        /**
         * Construct a GetNextBlocks message
         *
         * @param   blockId                 Start block identifier
         * @param   limit                   Maximum number of blocks
         * @param   blockIds                Block identifier list or null
         */
        public GetNextBlocksMessage(long blockId, int limit, List<Long> blockIds) {
            super("GetNextBlocks");
            if (blockIds != null && blockIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blockIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = nextMessageId.incrementAndGet();
            this.blockId = blockId;
            this.limit = limit;
            this.blockIds = (blockIds != null ? blockIds : Collections.emptyList());
        }

        /**
         * Construct a GetNextBlocks message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetNextBlocksMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetNextBlocks", bytes);
            this.messageId = bytes.getLong();
            this.blockId = bytes.getLong();
            this.limit = bytes.getInt();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.blockIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                blockIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 4 + 2 + (8 * blockIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putLong(blockId).putInt(limit).putShort((short)blockIds.size());
            blockIds.forEach(bytes::putLong);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the start block identifier
         *
         * @return                          Start block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Get the maximum number of blocks
         *
         * @return                          Maximum number of blocks
         */
        public int getLimit() {
            return limit;
        }

        /**
         * Get the block identifiers
         *
         * @return                          Block identifiers
         */
        public List<Long> getBlockIds() {
            return blockIds;
        }
    }

    /**
     * The GetBlock message is sent when a node is notified that a new block is available.
     * The Blocks message is returned in response.  The sender can include a list of transactions
     * to be excluded when creating the Blocks message.  The sender must then supply
     * the excluded transactions when it receives the Blocks message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Block identifier (long)
     * <li>Transaction exclusion BitSet (byte[])
     * </ul>
     */
    public static class GetBlockMessage extends NetworkMessage {

        /** Block identifier list */
        private final long blockId;

        /** Transaction exclusion BitSet */
        private final byte[] excludedTransactions;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetBlockMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetBlock.processRequest(node, this);
        }

        /**
         * Construct a GetBlocks message
         */
        private GetBlockMessage() {
            super("GetBlocks");
            messageId = 0;
            blockId = 0;
            excludedTransactions = null;
        }

        /**
         * Construct a GetBlocks message
         *
         * @param   blockId                Block identifier
         */
        public GetBlockMessage(long blockId) {
            this(blockId, null);
        }

        /**
         * Construct a GetBlock message
         *
         * Transactions can be excluded
         *
         * @param   blockId                Block identifier
         * @param   excludedTransactions    Excluded transactions or null
         */
        public GetBlockMessage(long blockId, BitSet excludedTransactions) {
            super("GetBlocks");
            this.excludedTransactions = excludedTransactions == null ? null : excludedTransactions.toByteArray();
            this.messageId = nextMessageId.incrementAndGet();
            this.blockId = blockId;
        }

        /**
         * Construct a GetBlock message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetBlockMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetBlocks", bytes);
            this.messageId = bytes.getLong();
            blockId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_ARRAY_LENGTH) {
                throw new NetworkException("Excluded transactions size " + count + " exceeds the maximum of " + MAX_ARRAY_LENGTH);
            }
            if (count > 0) {
                this.excludedTransactions = new byte[count];
                bytes.get(excludedTransactions);
            } else {
                this.excludedTransactions = null;
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 8 + 2 + excludedTransactions.length;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putLong(blockId);
            if (excludedTransactions != null) {
                bytes.putShort((short) excludedTransactions.length);
                bytes.put(excludedTransactions);
            } else {
                bytes.putShort((short)0);
            }
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the block identifier
         *
         * @return                          Block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Get the excluded transaction identifiers
         *
         * @return                          Transaction identifiers
         */
        public byte[] getExcludedTransactions() {
            return excludedTransactions;
        }
    }

    /**
     * The Blocks message is returned in response to the GetBlock and GetNextBlocks message.
     * The message identifier is obtained from the request message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Block list
     * </ul>
     */
    public static class BlocksMessage extends NetworkMessage {

        /** Blocks */
        private final List<BlockBytes> blockBytes;

        /** Total length */
        private int totalBlockLength;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new BlocksMessage(bytes);
        }

        /**
         * Construct a Blocks message
         */
        private BlocksMessage() {
            super("Blocks");
            messageId = 0;
            blockBytes = null;
            totalBlockLength = 0;
        }

        /**
         * Construct a Blocks message
         *
         * @param   messageId               Message identifier
         * @param   blocks                  Block list
         */
        public BlocksMessage(long messageId, List<? extends Block> blocks) {
            super("Blocks");
            if (blocks.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + blocks.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            blockBytes = new ArrayList<>(blocks.size());
            totalBlockLength = 0;
            for (Block block : blocks) {
                BlockBytes bytes = new BlockBytes(block);
                if (getLength() + bytes.getLength() > NetworkHandler.MAX_MESSAGE_SIZE) {
                    ((ArrayList)blockBytes).trimToSize();
                    break;
                }
                blockBytes.add(bytes);
                totalBlockLength += bytes.getLength();
            }
        }

        /**
         * Construct a Blocks message
         *
         * @param   messageId               Message identifier
         * @param   block                  Block
         * @param   excludedTransactions    transactions to exclude
         */
        public BlocksMessage(long messageId, Block block, byte[] excludedTransactions) {
            super("Blocks");
            this.messageId = messageId;
            totalBlockLength = 0;
            if (block == null) {
                blockBytes = Collections.emptyList();
            } else {
                BlockBytes bytes = excludedTransactions == null ? new BlockBytes(block) : new BlockBytes(block, BitSet.valueOf(excludedTransactions));
                blockBytes = Collections.singletonList(bytes);
                totalBlockLength += bytes.getLength();
            }
        }

        /**
         * Construct a Blocks message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private BlocksMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("Blocks", bytes);
            messageId = bytes.getLong();
            int blockCount = (int)bytes.getShort() & 0xffff;
            if (blockCount > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + blockCount + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            blockBytes = new ArrayList<>(blockCount);
            totalBlockLength = 0;
            for (int i=0; i<blockCount; i++) {
                BlockBytes block = new BlockBytes(bytes);
                blockBytes.add(block);
                totalBlockLength += block.getLength();
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + totalBlockLength;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)blockBytes.size());
            blockBytes.forEach((block) -> block.getBytes(bytes));
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the number of blocks
         *
         * @return                          Number of blocks
         */
        public int getBlockCount() {
            return blockBytes.size();
        }

        /**
         * Get the blocks
         *
         * This method cannot be used if transactions were excluded in the GetBlock message
         *
         * @return                          Block list
         * @throws  NotValidException       Block is not valid
         */
        public List<Block> getBlocks() throws NotValidException {
            List<Block> blocks = new ArrayList<>(blockBytes.size());
            for (BlockBytes bytes : blockBytes) {
                blocks.add(bytes.getBlock());
            }
            return blocks;
        }

        /**
         * Get the blocks
         *
         * This method must be used if transactions were excluded in the GetBlock message
         *
         * @param   excludedTransactions    Transactions that were excluded from the blocks
         * @return                          Block list
         * @throws  NotValidException       Block is not valid
         */
        public Block getBlock(List<Transaction> excludedTransactions) throws NotValidException {
            if (blockBytes.size() > 1) {
                throw new IllegalArgumentException("BlocksMessage of more than one block does not support excludedTransactions");
            }
            if (blockBytes.isEmpty()) {
                return null;
            }
            return blockBytes.get(0).getBlock(excludedTransactions);
        }
    }

    /**
     * The GetTransactions message is sent to retrieve one or more transactions.
     * The Transactions message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Transaction identifier list (long)
     * </ul>
     */
    public static class GetTransactionsMessage extends NetworkMessage {

        /** Transaction identifier list */
        private final List<ChainTransactionId> transactionIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetTransactionsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetTransactions.processRequest(node, this);
        }

        /**
         * Construct a GetTransactions message
         */
        private GetTransactionsMessage() {
            super("GetTransactions");
            this.messageId = 0;
            this.transactionIds = null;
        }

        /**
         * Construct a GetTransactions message
         *
         * @param   transactionIds              Transaction identifiers
         */
        public GetTransactionsMessage(List<ChainTransactionId> transactionIds) {
            super("GetTransactions");
            if (transactionIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactionIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = nextMessageId.incrementAndGet();
            this.transactionIds = transactionIds;
        }

        /**
         * Construct a GetTransactions message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetTransactionsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetTransactions", bytes);
            this.messageId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.transactionIds = new ArrayList<>(count);
            for (int i=0; i < count; i++) {
                transactionIds.add(ChainTransactionId.parse(bytes));
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        public int getLength() {
            return super.getLength() + 8 + 2 + (ChainTransactionId.BYTE_SIZE * transactionIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        public void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)transactionIds.size());
            transactionIds.forEach((id) -> id.put(bytes));
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Get the transaction identifiers
         *
         * @return                          Transaction identifiers
         */
        public List<ChainTransactionId> getTransactionIds() {
            return transactionIds;
        }
    }

    /**
     * The GetUnconfirmedTransactions message is sent to retrieve the current set
     * of unconfirmed transactions.
     * The Transactions message is returned in response.
     * <ul>
     * <li>Message identifier (long)
     * <li>Transaction exclusion list (long)
     * </ul>
     */
    public static class GetUnconfirmedTransactionsMessage extends NetworkMessage {

        /** Transaction exclusion list */
        private final List<Long> exclusionIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new GetUnconfirmedTransactionsMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return GetUnconfirmedTransactions.processRequest(node, this);
        }

        /**
         * Construct a GetUnconfirmedTransactions message
         */
        private GetUnconfirmedTransactionsMessage() {
            super("GetUnconfirmedTransactions");
            this.messageId = 0;
            this.exclusionIds = null;
        }

        /**
         * Construct a GetUnconfirmedTransactions message
         *
         * @param   exclusionIds                Sorted list of excluded transaction identifiers
         */
        public GetUnconfirmedTransactionsMessage(List<Long> exclusionIds) {
            super("GetUnconfirmedTransactions");
            if (exclusionIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + exclusionIds.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = nextMessageId.incrementAndGet();
            this.exclusionIds = exclusionIds;
        }

        /**
         * Construct a GetUnconfirmedTransactions message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private GetUnconfirmedTransactionsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("GetUnconfirmedTransactions", bytes);
            this.messageId = bytes.getLong();
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.exclusionIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                exclusionIds.add(bytes.getLong());
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + (8 * exclusionIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)exclusionIds.size());
            exclusionIds.forEach(bytes::putLong);
        }

        /**
         * Check if the message requires a response
         *
         * @return                              TRUE if the message requires a response
         */
        @Override
        boolean requiresResponse() {
            return true;
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the exclusions
         *
         * @return                          Exclusions
         */
        public List<Long> getExclusions() {
            return exclusionIds;
        }
    }

    /**
     * The Transactions message is returned in response to the GetTransactions and
     * GetUnconfirmedTransactions messages.
     * The message identifier is obtained from the request message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Transaction list
     * </ul>
     */
    public static class TransactionsMessage extends NetworkMessage {

        /** Transactions */
        private final List<TransactionBytes> transactionBytes;

        /** Total length */
        private int totalTransactionLength;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new TransactionsMessage(bytes);
        }

        /**
         * Construct a Transactions message
         */
        private TransactionsMessage() {
            super("Transactions");
            this.messageId = 0;
            this.transactionBytes = null;
            this.totalTransactionLength = 0;
        }

        /**
         * Construct a Transactions message
         *
         * @param   messageId               Message identifier
         * @param   transactions            Transaction list
         */
        public TransactionsMessage(long messageId, List<? extends Transaction> transactions) {
            super("Transactions");
            if (transactions.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactions.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.messageId = messageId;
            this.transactionBytes = new ArrayList<>(transactions.size());
            totalTransactionLength = 0;
            for (Transaction tx : transactions) {
                TransactionBytes bytes = new TransactionBytes(tx);
                if (getLength() + bytes.getLength() > NetworkHandler.MAX_MESSAGE_SIZE) {
                    ((ArrayList)transactionBytes).trimToSize();
                    break;
                }
                transactionBytes.add(bytes);
                totalTransactionLength += bytes.getLength();
            }
        }

        /**
         * Construct a Transactions message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private TransactionsMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("Transactions", bytes);
            this.messageId = bytes.getLong();
            int transactionCount = (int)bytes.getShort() & 0xffff;
            if (transactionCount > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + transactionCount + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            this.transactionBytes = new ArrayList<>(transactionCount);
            totalTransactionLength = 0;
            for (int i=0; i<transactionCount; i++) {
                TransactionBytes txBytes = new TransactionBytes(bytes);
                transactionBytes.add(txBytes);
                totalTransactionLength += txBytes.getLength();
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 2 + totalTransactionLength;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.putShort((short)transactionBytes.size());
            transactionBytes.forEach((tx) -> tx.getBytes(bytes));
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Get the transaction count
         *
         * @return                          Transaction count
         */
        public int getTransactionCount() {
            return transactionBytes.size();
        }

        /**
         * Get the transactions
         *
         * @return                          Transaction list
         * @throws  NotValidException       Transaction is not valid
         */
        public List<Transaction> getTransactions() throws NotValidException {
            List<Transaction> transactions = new ArrayList<>(transactionBytes.size());
            for (TransactionBytes bytes : transactionBytes) {
                transactions.add(bytes.getTransaction());
            }
            return transactions;
        }
    }

    /**
     * The BlockInventory message is sent when a node has received a new block.
     * The node responds with a GetBlock request if it wants to get the block.
     * <ul>
     * <li>Block identifier (long)
     * <li>Previous block identifier (long)
     * <li>Block timestamp (integer)
     * <li>Transaction identifier list (ChainTransactionId)
     * </ul>
     * The ordering of transactions in the transaction identifier list must be
     * same as the one used by BlockBytes when returning block and transaction
     * bytes, as the exclude transactions feature depends on this.
     */
    public static class BlockInventoryMessage extends NetworkMessage {

        /** Block identifier */
        private final long blockId;

        /** Previous block identifier */
        private final long previousBlockId;

        /** Block timestamp */
        private final int timestamp;

        /** Transaction identifiers */
        private final List<ChainTransactionId> transactionIds;

        private int[] childCounts;

        private int totalLength;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new BlockInventoryMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return BlockInventory.processRequest(node, this);
        }

        /**
         * Construct a BlockInventory message
         */
        private BlockInventoryMessage() {
            super("BlockInventory");
            blockId = 0;
            previousBlockId = 0;
            timestamp = 0;
            transactionIds = null;
            totalLength = 0;
        }

        /**
         * Construct a BlockInventory message
         *
         * @param   block                   Block
         */
        public BlockInventoryMessage(Block block) {
            super("BlockInventory");
            blockId = block.getId();
            previousBlockId = block.getPreviousBlockId();
            timestamp = block.getTimestamp();
            totalLength = 8 + 8 + 4 + 2;
            List<? extends SmcTransaction> transactions = block.getSmcTransactions();
            if (transactions.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactions.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            childCounts = new int[transactions.size()];
            transactionIds = new ArrayList<>();
            for (int i = 0; i < childCounts.length; i++) {
                SmcTransaction smcTransaction = transactions.get(i);
                transactionIds.add(ChainTransactionId.getChainTransactionId(smcTransaction));
                totalLength += 32; // smcTransactionHash
                int childCount = 0;
                totalLength += 2; // childCount
                if (childCount > 0) {
                    totalLength += 4 + childCount * 32; // childChainId and childTransactionHashes
                }
                childCounts[i] = childCount;
            }
        }

        /**
         * Construct a BlockInventory message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private BlockInventoryMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("BlockInventory", bytes);
            blockId = bytes.getLong();
            previousBlockId = bytes.getLong();
            timestamp = bytes.getInt();
            int count = (int)bytes.getShort() & 0xffff; //SmcTransaction count
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            int totalCount = count;
            transactionIds = new ArrayList<>();
            childCounts = new int[count];
            for (int i=0; i<count; i++) {
                byte[] smcTransactionHash = new byte[32];
                bytes.get(smcTransactionHash);
                transactionIds.add(new ChainTransactionId(Mainchain.mainchain.getId(), smcTransactionHash));
                int childCount = (int)bytes.getShort() & 0xffff;
                childCounts[i] = childCount;
                if (childCount > 0) {
                    if ((totalCount += childCount) > MAX_LIST_SIZE) {
                        throw new NetworkException("Total list size " + totalCount + " exceeds the maximum of " + MAX_LIST_SIZE);
                    }
                    int childChainId = bytes.getInt();
                    while (childCount-- > 0) {
                        byte[] childTransactionHash = new byte[32];
                        bytes.get(childTransactionHash);
                        transactionIds.add(new ChainTransactionId(childChainId, childTransactionHash));
                    }
                }
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + totalLength;
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(blockId).putLong(previousBlockId).putInt(timestamp);
            bytes.putShort((short)childCounts.length);
            Iterator<ChainTransactionId> iterator = transactionIds.iterator();
            for (int i = 0; i < childCounts.length; i++) {
                ChainTransactionId smcTransactionId = iterator.next();
                bytes.put(smcTransactionId.getFullHash());
                int childCount = childCounts[i];
                bytes.putShort((short)childCount);
                if (childCount-- > 0) {
                    ChainTransactionId childTransactionId = iterator.next();
                    bytes.putInt(childTransactionId.getChainId());
                    bytes.put(childTransactionId.getFullHash());
                }
                while (childCount-- > 0) {
                    bytes.put(iterator.next().getFullHash());
                }
            }
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the block identifier
         *
         * @return                          Block identifier
         */
        public long getBlockId() {
            return blockId;
        }

        /**
         * Get the previous block identifier
         *
         * @return                          Block identifier
         */
        public long getPreviousBlockId() {
            return previousBlockId;
        }

        /**
         * Get the timestamp
         *
         * @return                          Timestamp
         */
        public int getTimestamp() {
            return timestamp;
        }

        /**
         * Get the block transaction identifiers
         *
         * @return                          Transaction identifiers
         */
        public List<ChainTransactionId> getTransactionIds() {
            return transactionIds;
        }
    }

    /**
     * The TransactionsInventory message is sent when a node has received new transactions.
     * The node responds with a GetTransactions message if it wants to
     * receive the transactions.
     * TransactionsInventory messages also include the child transaction identifiers for
     * each of the ChildBlockTransactions in them.
     * <ul>
     * <li>Transaction list
     * </ul>
     * <p>
     * Each transaction list entry has the following format:
     * <ul>
     * <li>Transaction identifier (ChainTransactionId)
     * </ul>
     */
    public static class TransactionsInventoryMessage extends NetworkMessage {

        /** Transaction identifier */
        private final List<ChainTransactionId> transactionIds;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new TransactionsInventoryMessage(bytes);
        }

        /**
         * Process the message
         *
         * @param   node                        Node
         * @return                              Response message
         */
        @Override
        NetworkMessage processMessage(NodeImpl node) {
            return TransactionsInventory.processRequest(node, this);
        }

        /**
         * Construct a TransactionsInventory message
         */
        private TransactionsInventoryMessage() {
            super("TransactionsInventory");
            transactionIds = null;
        }

        /**
         * Construct a TransactionsInventory message
         *
         * @param   transactions                Transaction list
         */
        public TransactionsInventoryMessage(List<? extends Transaction> transactions) {
            super("TransactionsInventory");
            Set<ChainTransactionId> set = new HashSet<>();
            for (Transaction transaction : transactions) {
                set.add(ChainTransactionId.getChainTransactionId(transaction));
            }
            transactionIds = new ArrayList<>(set);
            if (transactionIds.size() > MAX_LIST_SIZE) {
                throw new RuntimeException("List size " + transactions.size() + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
        }

        /**
         * Construct a TransactionsInventory message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private TransactionsInventoryMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("TransactionsInventory", bytes);
            int count = (int)bytes.getShort() & 0xffff;
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("List size " + count + " exceeds the maximum of " + MAX_LIST_SIZE);
            }
            transactionIds = new ArrayList<>(count);
            for (int i=0; i<count; i++) {
                transactionIds.add(ChainTransactionId.parse(bytes));
            }
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 2 + (ChainTransactionId.BYTE_SIZE * transactionIds.size());
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putShort((short)transactionIds.size());
            for (ChainTransactionId transactionId : transactionIds) {
                transactionId.put(bytes);
            }
        }

        /**
         * Check if blockchain download is not allowed
         *
         * @return                              TRUE if blockchain download is not allowed
         */
        @Override
        boolean downloadNotAllowed() {
            return true;
        }

        /**
         * Get the transaction identifiers
         *
         * @return                          Identifier
         */
        public List<ChainTransactionId> getTransactionIds() {
            return transactionIds;
        }

    }

    /**
     * The Error message is returned when a error is detected while processing a
     * request.  No error is returned for messages that do not have a response.
     * The message identifier is obtained from the request message.
     * <ul>
     * <li>Message identifier (long)
     * <li>Error severity (boolean)
     * <li>Error name (string)
     * <li>Error message (string)
     * </ul>
     */
    public static class ErrorMessage extends NetworkMessage {

        /** Error message */
        private final byte[] errorMessageBytes;

        /** Message name */
        private final byte[] errorNameBytes;

        /** Error severity */
        private final boolean severeError;

        /**
         * Construct the message from the message bytes
         *
         * @param   bytes                       Message bytes following the message name
         * @return                              Message
         * @throws  BufferOverflowException     Message buffer is too small
         * @throws  BufferUnderflowException    Message is too short
         * @throws  NetworkException            Message is not valid
         */
        @Override
        protected NetworkMessage constructMessage(ByteBuffer bytes)
                                    throws BufferOverflowException, BufferUnderflowException, NetworkException {
            return new ErrorMessage(bytes);
        }

        /**
         * Construct am Error message
         */
        private ErrorMessage() {
            super("Error");
            messageId = 0;
            severeError = false;
            errorNameBytes = null;
            errorMessageBytes = null;
        }

        /**
         * Construct an Error message
         *
         * @param   messageId               Message identifier
         * @param   severeError             TRUE if this is a severe error
         * @param   errorName               Error name
         * @param   errorMessage            Error message
         */
        public ErrorMessage(long messageId, boolean severeError, String errorName, String errorMessage) {
            super("Error");
            this.messageId = messageId;
            this.severeError = severeError;
            this.errorNameBytes = errorName.getBytes(UTF8);
            this.errorMessageBytes = errorMessage.getBytes(UTF8);
        }

        /**
         * Construct an Error Message
         *
         * @param   bytes                       Message bytes
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Message is not valid
         */
        private ErrorMessage(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            super("Error", bytes);
            messageId = bytes.getLong();
            severeError = (bytes.get() != (byte)0);
            errorNameBytes = decodeArray(bytes);
            errorMessageBytes = decodeArray(bytes);
        }

        /**
         * Get the message length
         *
         * @return                      Message length
         */
        @Override
        int getLength() {
            return super.getLength() + 8 + 1 +
                    getEncodedArrayLength(errorNameBytes) +
                    getEncodedArrayLength(errorMessageBytes);
        }

        /**
         * Get the message bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        @Override
        void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            super.getBytes(bytes);
            bytes.putLong(messageId);
            bytes.put(severeError ? (byte)1 : (byte)0);
            encodeArray(bytes, errorNameBytes);
            encodeArray(bytes, errorMessageBytes);
        }

        /**
         * Check if the message is a response
         *
         * @return                              TRUE if this is a response message
         */
        @Override
        boolean isResponse() {
            return true;
        }

        /**
         * Check if this is a severe error
         *
         * @return                          TRUE if this is a severe error
         */
        public boolean isSevereError() {
            return severeError;
        }

        /**
         * Get the error name
         *
         * @return                          Error name
         */
        public String getErrorName() {
            return new String(errorNameBytes, UTF8);
        }

        /**
         * Get the error message
         *
         * @return                          Error Message
         */
        public String getErrorMessage() {
            return new String(errorMessageBytes, UTF8);
        }
    }

    /**
     * Encoded block bytes
     * <ul>
     * <li>Block
     * <li>Transaction list (missing transactions are represented by empty transaction bytes)
     * </ul>
     * The ordering of transactions must be same as used in the BlockInventoryMessage.
     */
    private static class BlockBytes {

        /** Block bytes */
        private final byte[] blockBytes;

        /** Block transactions, each SmcTransaction is followed by its ChildTransactions, if any */
        private final List<TransactionBytes> blockTransactions;

        /** Child transaction counts for each SmcTransaction */
        private final int[] childCounts;

        /** Total block byte length */
        private int length;

        /**
         * Construct an encoded block
         *
         * @param   block               Block
         */
        private BlockBytes(Block block) {
            blockBytes = block.getBytes();
            length = getEncodedArrayLength(blockBytes) + 2; // smcTransactions count (short)
            List<? extends SmcTransaction> transactions = block.getSmcTransactions();
            blockTransactions = new ArrayList<>();
            childCounts = new int[transactions.size()];
            for (int i = 0; i < childCounts.length; i++) {
                SmcTransaction smcTransaction = transactions.get(i);
                TransactionBytes transactionBytes = new TransactionBytes(smcTransaction);
                blockTransactions.add(transactionBytes);
                length += transactionBytes.getLength() + 2; // childTransactions count (short)
                childCounts[i] = 0;
            }
        }

        /**
         * Construct an encoded block
         *
         * @param   block               Block
         * @param   excludedTransactions    transactions to exclude
         */
        private BlockBytes(Block block, BitSet excludedTransactions) {
            blockBytes = block.getBytes();
            length = getEncodedArrayLength(blockBytes) + 2;
            List<? extends SmcTransaction> transactions = block.getSmcTransactions();
            blockTransactions = new ArrayList<>();
            childCounts = new int[transactions.size()];
            int index = 0;
            for (int i = 0; i < childCounts.length; i++) {
                SmcTransaction smcTransaction = transactions.get(i);
                if (!excludedTransactions.get(index++)) {
                    TransactionBytes transactionBytes = new TransactionBytes(smcTransaction);
                    blockTransactions.add(transactionBytes);
                    length += transactionBytes.getLength();
                } else {
                    blockTransactions.add(TransactionBytes.EXCLUDED);
                    length += TransactionBytes.EXCLUDED.getLength();
                }
                length += 2; // childTransactions count (short) always included
                childCounts[i] = 0;
            }
        }

        /**
         * Construct an encoded block
         *
         * @param   bytes                       Message buffer
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Block is not valid
         */
        private BlockBytes(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            blockBytes = decodeArray(bytes);
            length = getEncodedArrayLength(blockBytes) + 2;
            int count = (int)bytes.getShort() & 0xffff; //SmcTransaction count
            if (count > MAX_LIST_SIZE) {
                throw new NetworkException("Array size " + count + " exceeds the maximum size");
            }
            int totalCount = count;
            blockTransactions = new ArrayList<>();
            childCounts = new int[count];
            for (int i = 0; i < count; i++) {
                TransactionBytes smcTransactionBytes = TransactionBytes.parse(bytes);
                blockTransactions.add(smcTransactionBytes);
                length += smcTransactionBytes.getLength() + 2; //child count
                int childCount = (int)bytes.getShort() & 0xffff;
                childCounts[i] = childCount;
                if ((totalCount += childCount) > MAX_LIST_SIZE) {
                    throw new NetworkException("Array size " + totalCount + " exceeds the maximum size");
                }
                while (childCount-- > 0) {
                    TransactionBytes childTransactionBytes = TransactionBytes.parse(bytes);
                    blockTransactions.add(childTransactionBytes);
                    length += childTransactionBytes.getLength();
                }
            }
        }

        /**
         * Get the encoded block size
         *
         * @return                      Encoded block size
         */
        private int getLength() {
            return length;
        }

        /**
         * Get the encoded block bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Buffer is too small
         */
        private void getBytes(ByteBuffer bytes) throws BufferOverflowException {
            encodeArray(bytes, blockBytes);
            bytes.putShort((short)childCounts.length);
            Iterator<TransactionBytes> iterator = blockTransactions.iterator();
            for (int childCount : childCounts) {
                iterator.next().getBytes(bytes);
                bytes.putShort((short) childCount);
                while (childCount-- > 0) {
                    iterator.next().getBytes(bytes);
                }
            }
        }

        /**
         * Get the block
         *
         * This method cannot be used if transactions were excluded in the GetBlocks message
         *
         * @return                      Block
         * @throws  NotValidException   Block is not valid
         */
        private Block getBlock() throws NotValidException {
            /*List<SmcTransaction> smcTransactions = new ArrayList<>(childCounts.length);
            return Shareschain.parseBlock(blockBytes, smcTransactions);*/

            List<SmcTransaction> smcTransactions = new ArrayList<>(childCounts.length);
            Iterator<TransactionBytes> iterator = blockTransactions.iterator();
            for (int childCount : childCounts) {
                SmcTransaction smcTransaction = (SmcTransaction)iterator.next().getTransaction();

                smcTransactions.add(smcTransaction);
            }
            return Shareschain.parseBlock(blockBytes, smcTransactions);


        }

        /**
         * Get the block
         *
         * This method must be used if transactions were excluded in the GetBlocks message
         *
         * @param   excludedTransactions Excluded transactions
         * @throws  NotValidException   Block is not valid
         */
        private Block getBlock(List<Transaction> excludedTransactions) throws NotValidException {
            /*if (excludedTransactions.isEmpty()) {
                return getBlock();
            }
            List<SmcTransaction> smcTransactions = new ArrayList<>(childCounts.length);
            Logger.logInfoMessage("childCounts.length === "+childCounts.length);
            Iterator<TransactionBytes> iterator = blockTransactions.iterator();
            Iterator<Transaction> excluded = excludedTransactions.iterator();
            return Shareschain.parseBlock(blockBytes, smcTransactions);*/

            if (excludedTransactions.isEmpty()) {
                return getBlock();
            }
            List<SmcTransaction> smcTransactions = new ArrayList<>(childCounts.length);
            Iterator<TransactionBytes> iterator = blockTransactions.iterator();
            Iterator<Transaction> excluded = excludedTransactions.iterator();
            for (int childCount : childCounts) {
                SmcTransaction smcTransaction = (SmcTransaction)iterator.next().getTransaction(excluded);

                smcTransactions.add(smcTransaction);
            }
            return Shareschain.parseBlock(blockBytes, smcTransactions);

        }
    }

    /**
     * Encoded transaction bytes
     * <p>
     * <ul>
     * <li>Transaction bytes (an excluded transaction consists of just the transaction identifier)
     * </ul>
     */
    private static class TransactionBytes {

        private static final TransactionBytes EXCLUDED = new TransactionBytes(Convert.EMPTY_BYTE);

        private static TransactionBytes parse(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            byte[] transactionBytes = decodeArray(bytes);
            if (transactionBytes.length == 0) {
                return EXCLUDED;
            }
            return new TransactionBytes(transactionBytes);
        }

        /** Transaction bytes */
        private final byte[] transactionBytes;

        /**
         * Construct an encoded transaction
         *
         * @param   transaction         Transaction
         */
        private TransactionBytes(Transaction transaction) {
            transactionBytes = transaction.getPrunableBytes();
        }

        /**
         * Construct an encoded transaction
         *
         * @param   transactionBytes    transaction bytes
         */
        private TransactionBytes(byte[] transactionBytes) {
            this.transactionBytes = transactionBytes;
        }

        /**
         * Construct an encoded transaction
         *
         * @param   bytes                       Message buffer
         * @throws  BufferUnderflowException    Message is too small
         * @throws  NetworkException            Transaction is not valid
         */
        private TransactionBytes(ByteBuffer bytes) throws BufferUnderflowException, NetworkException {
            transactionBytes = decodeArray(bytes);
        }

        /**
         * Get the encoded transaction length
         *
         * @return                      Encoded transaction length
         */
        private int getLength() {
            return getEncodedArrayLength(transactionBytes);
        }

        /**
         * Get the encoded transaction bytes
         *
         * @param   bytes                       Message buffer
         * @throws  BufferOverflowException     Message buffer is too small
         */
        private void getBytes(ByteBuffer bytes) {
            encodeArray(bytes, transactionBytes);
        }

        /**
         * Get the transaction
         *
         * This method cannot be used if transactions were excluded
         *
         * @return                      Transaction
         * @throws  NotValidException   Transaction is not valid
         */
        private Transaction getTransaction() throws NotValidException {
            if (transactionBytes.length == 0) {
                throw new IllegalArgumentException("No excluded transactions provided");
            }
            return Shareschain.parseTransaction(transactionBytes);
        }

        /**
         * Get the transaction
         *
         * This method must be used if transactions were excluded
         *
         * @param   excluded    Excluded transactions iterator
         * @throws  NotValidException       Transaction is not valid
         */
        private Transaction getTransaction(Iterator<Transaction> excluded) throws NotValidException {
            if (transactionBytes.length != 0) {
                return getTransaction();
            }
            return excluded.next();
        }
    }
}
