import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Set;

/** Generates a P-256 VAPID key pair for standards-based Web Push. */
public final class VapidKeyGenerator {
    private static final Path PRIVATE_KEY_FILE = Path.of("vapid-private.key");
    private static final Base64.Encoder BASE64_URL = Base64.getUrlEncoder().withoutPadding();

    private VapidKeyGenerator() {}

    public static void main(String[] args) throws Exception {
        if (Files.exists(PRIVATE_KEY_FILE)) {
            throw new IllegalStateException(PRIVATE_KEY_FILE + " already exists; move it aside before generating a new pair.");
        }

        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair pair = generator.generateKeyPair();

        ECPublicKey publicKey = (ECPublicKey) pair.getPublic();
        ECPrivateKey privateKey = (ECPrivateKey) pair.getPrivate();
        byte[] publicBytes = new byte[65];
        publicBytes[0] = 0x04;
        copy32(publicKey.getW().getAffineX(), publicBytes, 1);
        copy32(publicKey.getW().getAffineY(), publicBytes, 33);
        String publicValue = BASE64_URL.encodeToString(publicBytes);
        String privateValue = BASE64_URL.encodeToString(to32Bytes(privateKey.getS()));

        Files.writeString(PRIVATE_KEY_FILE, privateValue + System.lineSeparator(), StandardCharsets.US_ASCII);
        try {
            Files.setPosixFilePermissions(PRIVATE_KEY_FILE, EnumSet.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // The generated file is still excluded by Git on platforms without POSIX permissions.
        }

        System.out.println("VAPID_PUBLIC_KEY=" + publicValue);
        System.out.println("Private key saved to " + PRIVATE_KEY_FILE.toAbsolutePath());
    }

    private static void copy32(BigInteger value, byte[] target, int offset) {
        byte[] bytes = to32Bytes(value);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private static byte[] to32Bytes(BigInteger value) {
        byte[] encoded = value.toByteArray();
        byte[] result = new byte[32];
        int sourceOffset = Math.max(0, encoded.length - result.length);
        int length = Math.min(encoded.length, result.length);
        System.arraycopy(encoded, sourceOffset, result, result.length - length, length);
        return result;
    }
}
