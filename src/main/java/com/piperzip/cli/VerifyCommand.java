package com.piperzip.cli;

import com.piperzip.integrity.Sha256Hasher;
import com.piperzip.validation.PathValidator;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Parameters;

/** {@code piperzip verify}. */
@Command(name = "verify", mixinStandardHelpOptions = true,
        description = "Check that two files are byte-for-byte identical by comparing SHA-256 hashes.")
final class VerifyCommand extends BaseCommand {

    @Parameters(index = "0", paramLabel = "<original>", description = "The reference file.")
    private Path original;

    @Parameters(index = "1", paramLabel = "<restored>", description = "The file to compare against it.")
    private Path restored;

    @Mixin
    private Mixins.JsonOutput json;

    @Override
    public Integer call() throws Exception {
        PathValidator.requireReadableFile(original);
        PathValidator.requireReadableFile(restored);
        String hashA = Sha256Hasher.hash(original);
        String hashB = Sha256Hasher.hash(restored);
        boolean match = hashA.equals(hashB);
        if (json.json) {
            out().println("{\"operation\":\"verify\",\"original\":" + Json.quote(original.toString())
                    + ",\"restored\":" + Json.quote(restored.toString()) + ",\"sha256Original\":\"" + hashA
                    + "\",\"sha256Restored\":\"" + hashB + "\",\"match\":" + match + "}");
        } else {
            out().println("SHA-256 " + hashA + "  " + original);
            out().println("SHA-256 " + hashB + "  " + restored);
            out().println(match ? "OK: the files are identical." : "MISMATCH: the files differ.");
        }
        out().flush();
        return match ? ExitCodes.OK : ExitCodes.VERIFY_MISMATCH;
    }
}
