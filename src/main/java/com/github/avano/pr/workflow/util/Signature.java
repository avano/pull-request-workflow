package com.github.avano.pr.workflow.util;

import org.apache.commons.codec.binary.Hex;

import com.github.avano.pr.workflow.config.Configuration;
import com.github.avano.pr.workflow.config.ConfigurationException;

import javax.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * Utility class for working with signatures.
 */
@ApplicationScoped
public class Signature {
    private static final String GH_SIGNATURE_ALGORITHM = "HmacSHA1";
    private Mac mac;

    @Inject
    Configuration config;

    @PostConstruct
    public void init() {
        try {
            mac = Mac.getInstance(GH_SIGNATURE_ALGORITHM);
            mac.init(new SecretKeySpec(config.getSecret().getBytes(), GH_SIGNATURE_ALGORITHM));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new ConfigurationException("Invalid configuration: " + e);
        }
    }

    /**
     * Computes the signature of given payload using the configured secret.
     * @param payload payload
     * @return signature in expected form
     */
    public String compute(byte[] payload) {
        return "sha1=" + new String(Hex.encodeHex(mac.doFinal(payload)));
    }

    /**
     * Checks if the actual signature of the request matches the expected signature.
     * @param actual actual signature sent by github
     * @param payload payload
     * @return true/false
     */
    public boolean isValid(String actual, byte[] payload) {
        return actual.equals(compute(payload));
    }
}
