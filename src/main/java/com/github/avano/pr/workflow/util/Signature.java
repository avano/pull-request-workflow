package com.github.avano.pr.workflow.util;

import org.apache.commons.codec.binary.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.ApplicationScoped;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for working with signatures.
 */
@ApplicationScoped
public class Signature {
    private static final Logger LOG = LoggerFactory.getLogger(Signature.class);
    private static final String GH_SIGNATURE_ALGORITHM = "HmacSHA1";

    /**
     * Computes the signature of given payload using the configured secret.
     *
     * @param secret repository secret
     * @param payload payload
     * @return signature in expected form
     */
    public String compute(String secret, byte[] payload) {
        Mac mac;
        try {
            mac = Mac.getInstance(GH_SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(), GH_SIGNATURE_ALGORITHM));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            LOG.error("Unable to compute signature: " + e);
            return null;
        }
        return "sha1=" + new String(Hex.encodeHex(mac.doFinal(payload)));
    }

    /**
     * Checks if the actual signature of the request matches the expected signature.
     *
     * @param webhookSecret webhook secret for given repository
     * @param actual actual signature sent by github
     * @param payload event payload
     * @return true/false
     */
    public boolean isValid(String webhookSecret, String actual, byte[] payload) {
        return actual.equals(compute(webhookSecret, payload));
    }
}
