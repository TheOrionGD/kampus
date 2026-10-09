package javax.security.sasl;

import java.util.Enumeration;
import java.util.Map;
import javax.security.auth.callback.CallbackHandler;

public class Sasl {
    public static final String POLICY_NOPLAINTEXT = "javax.security.sasl.policy.noplaintext";
    public static final String POLICY_NOACTIVE = "javax.security.sasl.policy.noactive";
    public static final String POLICY_NODICTIONARY = "javax.security.sasl.policy.nodictionary";
    public static final String POLICY_NOANONYMOUS = "javax.security.sasl.policy.noanonymous";
    public static final String POLICY_FORWARD_SECRECY = "javax.security.sasl.policy.forward";
    public static final String POLICY_PASS_CREDENTIALS = "javax.security.sasl.policy.credentials";
    public static final String QOP = "javax.security.sasl.qop";
    public static final String STRENGTH = "javax.security.sasl.strength";
    public static final String SERVER_AUTH = "javax.security.sasl.server.authentication";
    public static final String MAX_BUFFER = "javax.security.sasl.maxbuffer";

    private Sasl() {}

    public static SaslClient createSaslClient(
        String[] mechanisms,
        String authorizationId,
        String protocol,
        String serverName,
        Map<String, ?> props,
        CallbackHandler cbh
    ) throws SaslException {
        return null;
    }

    public static Enumeration<SaslClientFactory> getSaslClientFactories() {
        return java.util.Collections.emptyEnumeration();
    }
}
