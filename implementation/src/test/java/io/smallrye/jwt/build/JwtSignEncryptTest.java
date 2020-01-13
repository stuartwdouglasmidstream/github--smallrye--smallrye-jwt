/*
 *   Copyright 2019 Red Hat, Inc, and individual contributors.
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package io.smallrye.jwt.build;

import java.security.Key;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Map;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jose4j.base64url.Base64Url;
import org.jose4j.json.JsonUtil;
import org.jose4j.jwa.AlgorithmConstraints;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.junit.Assert;
import org.junit.Test;

import io.smallrye.jwt.KeyUtils;

public class JwtSignEncryptTest {

    @Test
    public void testSimpleInnerSignAndEncryptWithPemRsaPublicKey() throws Exception {
        String jweCompact = Jwt.claims()
                .claim("customClaim", "custom-value")
                .innerSign()
                .encrypt();

        checkJweHeaders(jweCompact, "RSA-OAEP-256", null);

        JsonWebEncryption jwe = getJsonWebEncryption(jweCompact);

        String jwtCompact = jwe.getPlaintextString();

        JsonWebSignature jws = getVerifiedJws(jwtCompact);
        JwtClaims claims = JwtClaims.parse(jws.getPayload());

        Assert.assertEquals(4, claims.getClaimsMap().size());
        checkClaimsAndJwsHeaders(jwtCompact, claims, "RS256", null);

        Assert.assertEquals("custom-value", claims.getClaimValue("customClaim"));
    }

    @Test
    public void testInnerSignAndEncryptWithPemRsaPublicKeyWithHeaders() throws Exception {
        String jweCompact = Jwt.claims()
                .claim("customClaim", "custom-value")
                .jws()
                .signatureKeyId("sign-key-id")
                .innerSign()
                .keyEncryptionKeyId("key-enc-key-id")
                .encrypt();

        checkJweHeaders(jweCompact, "RSA-OAEP-256", "key-enc-key-id");

        JsonWebEncryption jwe = getJsonWebEncryption(jweCompact);

        String jwtCompact = jwe.getPlaintextString();

        JsonWebSignature jws = getVerifiedJws(jwtCompact);
        JwtClaims claims = JwtClaims.parse(jws.getPayload());

        Assert.assertEquals(4, claims.getClaimsMap().size());
        checkClaimsAndJwsHeaders(jwtCompact, claims, "RS256", "sign-key-id");

        Assert.assertEquals("custom-value", claims.getClaimValue("customClaim"));
    }

    @Test
    public void testInnerSignNoneAndEncryptWithPemRsaPublicKey() throws Exception {
        JwtBuildConfigSource configSource = getConfigSource();
        configSource.setSigningKeyAvailability(false);
        String jweCompact = null;
        try {
            jweCompact = Jwt.claims()
                    .claim("customClaim", "custom-value")
                    .jws()
                    .innerSign()
                    .keyEncryptionKeyId("key-enc-key-id")
                    .encrypt();
        } finally {
            configSource.setSigningKeyAvailability(true);
        }

        checkJweHeaders(jweCompact, "RSA-OAEP-256", "key-enc-key-id");

        JsonWebEncryption jwe = getJsonWebEncryption(jweCompact);

        String jwtCompact = jwe.getPlaintextString();

        JsonWebSignature jws = getVerifiedJws(jwtCompact, null);
        JwtClaims claims = JwtClaims.parse(jws.getPayload());

        Assert.assertEquals(4, claims.getClaimsMap().size());
        checkClaimsAndJwsHeaders(jwtCompact, claims, "none", null);

        Assert.assertEquals("custom-value", claims.getClaimValue("customClaim"));
    }

    @Test
    public void testInnerSignAndEncryptWithJwkRsaPublicKey() throws Exception {
        JwtBuildConfigSource configSource = getConfigSource();
        configSource.setEncryptionKeyLocation("/publicKey.jwk");
        String jweCompact = null;
        try {
            jweCompact = Jwt.claims()
                    .claim("customClaim", "custom-value")
                    .jws()
                    .signatureKeyId("sign-key-id")
                    .innerSign()
                    .keyEncryptionKeyId("key1")
                    .encrypt();
        } finally {
            configSource.setEncryptionKeyLocation("/publicKey.pem");
        }

        checkJweHeaders(jweCompact, "RSA-OAEP-256", "key1");

        JsonWebEncryption jwe = getJsonWebEncryption(jweCompact);

        String jwtCompact = jwe.getPlaintextString();

        JsonWebSignature jws = getVerifiedJws(jwtCompact);
        JwtClaims claims = JwtClaims.parse(jws.getPayload());

        Assert.assertEquals(4, claims.getClaimsMap().size());
        checkClaimsAndJwsHeaders(jwtCompact, claims, "RS256", "sign-key-id");

        Assert.assertEquals("custom-value", claims.getClaimValue("customClaim"));
    }

    private static JwtBuildConfigSource getConfigSource() {
        for (ConfigSource cs : ConfigProvider.getConfig().getConfigSources()) {
            if (cs instanceof JwtBuildConfigSource) {
                return (JwtBuildConfigSource) cs;
            }
        }
        return null;
    }

    private static PrivateKey getPrivateKey() throws Exception {
        return KeyUtils.readPrivateKey("/privateKey.pem");
    }

    private static PublicKey getPublicKey() throws Exception {
        return KeyUtils.readPublicKey("/publicKey.pem");
    }

    private static JsonWebSignature getVerifiedJws(String jwt) throws Exception {
        return getVerifiedJws(jwt, getPublicKey());
    }

    private static JsonWebSignature getVerifiedJws(String jwt, Key key) throws Exception {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setCompactSerialization(jwt);
        jws.setKey(key);
        if (key == null) {
            jws.setAlgorithmConstraints(AlgorithmConstraints.ALLOW_ONLY_NONE);
        }
        Assert.assertTrue(jws.verifySignature());
        return jws;
    }

    private static void checkClaimsAndJwsHeaders(String jwsCompact, JwtClaims claims, String algo, String keyId)
            throws Exception {
        Assert.assertNotNull(claims.getIssuedAt());
        Assert.assertNotNull(claims.getExpirationTime());
        Assert.assertNotNull(claims.getJwtId());

        Map<String, Object> headers = getJwsHeaders(jwsCompact);
        Assert.assertEquals(keyId != null ? 3 : 2, headers.size());
        Assert.assertEquals(algo, headers.get("alg"));
        Assert.assertEquals("JWT", headers.get("typ"));
        if (keyId != null) {
            Assert.assertEquals(keyId, headers.get("kid"));
        } else {
            Assert.assertNull(headers.get("kid"));
        }
    }

    private static void checkJweHeaders(String jweCompact, String keyEncKeyAlg, String keyId) throws Exception {
        Map<String, Object> jweHeaders = getJweHeaders(jweCompact);
        Assert.assertEquals(keyId != null ? 4 : 3, jweHeaders.size());
        Assert.assertEquals(keyEncKeyAlg, jweHeaders.get("alg"));
        Assert.assertEquals("A256GCM", jweHeaders.get("enc"));
        if (keyId != null) {
            Assert.assertEquals(keyId, jweHeaders.get("kid"));
        }
        Assert.assertEquals("JWT", jweHeaders.get("cty"));
    }

    private static JsonWebEncryption getJsonWebEncryption(String compactJwe) throws Exception {
        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.setCompactSerialization(compactJwe);
        jwe.setKey(getPrivateKey());
        return jwe;
    }

    private static Map<String, Object> getJweHeaders(String compactJwe) throws Exception {
        int firstDot = compactJwe.indexOf(".");
        String headersJson = new Base64Url().base64UrlDecodeToUtf8String(compactJwe.substring(0, firstDot));
        return JsonUtil.parseJson(headersJson);
    }

    private static Map<String, Object> getJwsHeaders(String compactJws) throws Exception {
        int firstDot = compactJws.indexOf(".");
        String headersJson = new Base64Url().base64UrlDecodeToUtf8String(compactJws.substring(0, firstDot));
        return JsonUtil.parseJson(headersJson);
    }
}
