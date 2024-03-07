package net.earthcomputer.clientcommands.features;

import com.seedfinding.latticg.math.component.BigMatrix;
import com.seedfinding.latticg.math.component.BigVector;
import com.seedfinding.latticg.math.lattice.enumerate.EnumerateRt;
import com.seedfinding.latticg.math.optimize.Optimize;
import com.seedfinding.latticg.util.DeserializeRt;
import java.util.stream.LongStream;

// CLASS GENERATED BY LATTICG, DO NOT EDIT MANUALLY
public final class CCrackRngGen {
    private CCrackRngGen() {}
    private static final BigMatrix BASIS = DeserializeRt.mat(
        "ਊ\ued95膕쩅˴ꋄꊝ\ue001ʁ銧릿挂\ua7ebꇽ\ue702ʬ\uf198믅씁˚辑톹鄁˝ꊘ\udae2\udf02ʝ\ue7bf\ue0b1\ue602ˬ삭겦褂ʎ뇽\udeb1윁ʭ鮊\ue8cd쨂ˍ雏ꧮᄂ膲\uf5ba霆ʇ풁菆밁ˬ꺆\uf3e6" +
        "ꔁ˺횪钖܂\ue4e9\ude80\uf6afȂ\udda2飚\ue2dfȂ곶\ue5ecꌱʓ\ud999쿀稂钇잕ꄥ˴鞹킔ं肮颖鋀Ă\ue788鶆뎩Ă계\uf0b1谫˧귁臝숃ʤ뺎螴鈁ˤ\ue9de胶꼂ʕﲥꓵᴂ캸싲\ue4a4Ђ궮뢉难Ă췑뗧\uf4fc" +
        "Ă臲뷒草ʺ뛪ꚪ윁ˬ뇲\uf19cꈁʺ솋飖츁ˤ\udaef\uea8d\ue001ʤ뺎螴鈁ʬ\ue994궶쐂ʮꚍ\ue683ᜂ铄鲎낺Ȃ\uf4acꆚ\uef89Ă胮잶贵ʧ횤쿔鸃ʬ\uf7d6\udab2ﬁʧ鯈ꮴ頂ʤ뾋쯡搂\ue4da\uefea跠Ă\ueca6" +
        "톀\uf19aĂ\uf3cf藪谘˔\udeba\uda92茁ʴ鏋뺌퐂ʀ컩铚\uda01ʇ\uef9bꂸ㜂\uec94馷ﻏĂ\ufafb퇮\ue89d̂鶔薚켑ʤ뾋쯡搂겼襁鉵ˮ闵\ueb9bꨂ˭\ude98\uf2fb茁˴\ue1a4ꛝ愂肮\ue7e3鰞" +
        "˧폤\ufae2똁ʬ諤쯬켂ʚ쟐雂䄂\udd9f馕ሂ鶔薚켑ʕ훔힢蔂ˎ鞡룩\uf201ʭ\uf4c8웰㸂뒘\udb83뫆Ă臲ﺈ\ue485Ђ못ﾷ\udaecȂ閨\uedf3藧Ă쟹\ued91쐜ˤ鲈\uecff갂˝龙锒ʬ\uefae뫇圂껵\ud9a8ﺹĂ\uede1" +
        "\uf0e8\ue0a7Ȃ跉铫꾞Ă膒覦︕ʧ膉黖漂햂탎鲮̂Ꞇꎪｖ˝\uded7軟蔁ˤ鲈\uecff갂ˬ貀횘ꈁ˳郍킇툂ʭ\ua7db賧\u0602뒽ꊽ钾Ȃ胎裐ꋃȂ蝹킜탚Ă\uecfa\uf6af验Ă\ufae0\udf89衕ʝ鋠횅褂˝\uded7軟蔁˕ﶵ" +
        "싳옃ʓ뮈ꚋ\uf101Ȁ");
    private static final BigMatrix ROOT_INV = DeserializeRt.mat(
        "ਊ\u0380肀肀耐ހ肀肀耠ᎀ肀肀肀ċ肀肀肀䀆肀肀肀䀂肀肀肀䀏肀肀肀䀋肀肀肀老\u0b80肀肀耠\u0b80肀肀聀ڀ肀肀聀ኀ肀肀肀Ć肀肀肀老ྀ肀肀肀Ě肀肀肀老\u0e80肀肀聀\u0380肀肀耠ኀ肀肀聀 肀肀肀Ģ肀肀肀老\u0380肀肀耠᪀肀肀肀Ģ肀" +
        "肀肀老ʀ肀肀聀ᎀ肀肀肀Ě肀肀肀老ᮀ肀肀聀ᾀ肀肀肀ă肀肀肀䀖肀肀肀老ʀ肀肀耠ྀ肀肀肀Ă肀肀肀ည肀肀肀 肀肀肀 肀肀肀ဟ肀肀肀老\u0e80肀肀肀ă肀肀肀ဦ肀肀肀老ʀ肀肀耐\u0b80肀肀肀ć肀肀肀老ڀ肀肀耐⊀肀肀肀ě肀肀肀老ʀ肀肀耈\u0380肀肀肀ď" +
        "肀肀肀老ڀ肀肀耠\u0b80肀肀聀ހ肀肀肀ģ肀肀肀老\u0380肀肀耠ក肀肀肀Ċ肀肀肀 肀肀肀䀃肀肀肀 ț肀肀肀老➀肀肀肀Ħ肀肀肀老ʀ肀肀聀\2ʀ肀肀肀Ċ肀肀肀䀋肀肀肀䀎肀肀肀老\u0380肀肀耐\u0380肀肀聀ྀ肀肀肀ă肀肀肀ࠀȎ肀肀肀䀖肀肀肀老" +
        "ڀ肀肀肀Ă肀肀肀ࠆ肀肀肀䀞肀肀肀老ڀ肀肀聀Ẁ肀肀肀Ć肀肀肀䀆肀肀肀䀆肀肀肀 肀肀肀ည肀肀肀老⾀肀肀肀ă肀肀肀老ʀ肀肀耠\2ʀ肀肀耠\u0380肀肀耠᪀肀肀肀ć肀肀肀䀟肀肀肀老ڀ肀肀聀\u0a80肀肀肀ă肀肀肀老ހ肀肀聀ހ肀肀耠");
    private static final BigVector ORIGIN = DeserializeRt.vec(
        "\u0a00ʈ뒦벎츖ʐ駗질긾ʘ뾖黱덪ʠ뚊ꃪ쀆ʨ躛\ue6d4锦ʰ힒볩ﱁʸꆽ\uaaf8葉ˀﲈ쎉錏ˈ\uf8a5\uf19e똠Ȁ");
    private static final BigVector ROOT_ORIGIN = DeserializeRt.vec(
        "\u0ad7醌뗤ꪜހ肀肀耠횑貵\ue4aaﰃ肀肀肀‟ࣞ뚂\uf1e0뮚Ҁ肀肀耠骤蟓ꊳ輆肀肀肀⃟뚂\uf1e0뭲肀肀肀ဦ\u089aꒇ펢뎧ڀ肀肀耠\udeb6英\ue0bb㺀肀肀耐\udfb6英\ue0bbʀ肀肀耠");

    /**
     * Finds all values of {@code seed} that could produce the given results in the following code:
     * <pre>{@code
     *    Random rand = new Random(seed ^ 0x5DEECE66DL);
     *    // Go backwards by 39 random calls
     *    float nextFloat1 = rand.nextFloat();
     *    assert nextFloat1 >= minNextFloat1 && nextFloat1 < maxNextFloat1;
     *    // Skip 3 random calls
     *    float nextFloat2 = rand.nextFloat();
     *    assert nextFloat2 >= minNextFloat2 && nextFloat2 < maxNextFloat2;
     *    // Skip 3 random calls
     *    float nextFloat3 = rand.nextFloat();
     *    assert nextFloat3 >= minNextFloat3 && nextFloat3 < maxNextFloat3;
     *    // Skip 3 random calls
     *    float nextFloat4 = rand.nextFloat();
     *    assert nextFloat4 >= minNextFloat4 && nextFloat4 < maxNextFloat4;
     *    // Skip 3 random calls
     *    float nextFloat5 = rand.nextFloat();
     *    assert nextFloat5 >= minNextFloat5 && nextFloat5 < maxNextFloat5;
     *    // Skip 3 random calls
     *    float nextFloat6 = rand.nextFloat();
     *    assert nextFloat6 >= minNextFloat6 && nextFloat6 < maxNextFloat6;
     *    // Skip 3 random calls
     *    float nextFloat7 = rand.nextFloat();
     *    assert nextFloat7 >= minNextFloat7 && nextFloat7 < maxNextFloat7;
     *    // Skip 3 random calls
     *    float nextFloat8 = rand.nextFloat();
     *    assert nextFloat8 >= minNextFloat8 && nextFloat8 < maxNextFloat8;
     *    // Skip 3 random calls
     *    float nextFloat9 = rand.nextFloat();
     *    assert nextFloat9 >= minNextFloat9 && nextFloat9 < maxNextFloat9;
     *    // Skip 3 random calls
     *    float nextFloat10 = rand.nextFloat();
     *    assert nextFloat10 >= minNextFloat10 && nextFloat10 < maxNextFloat10;
     * }</pre>
     *
     * <p>This code skips 0.000000% of seeds in its search.
     */
    public static LongStream getSeeds(float minNextFloat1, float maxNextFloat1, float minNextFloat2, float maxNextFloat2, float minNextFloat3, float maxNextFloat3, float minNextFloat4, float maxNextFloat4, float minNextFloat5, float maxNextFloat5, float minNextFloat6, float maxNextFloat6, float minNextFloat7, float maxNextFloat7, float minNextFloat8, float maxNextFloat8, float minNextFloat9, float maxNextFloat9, float minNextFloat10, float maxNextFloat10) {
        Optimize.Builder builder = Optimize.Builder.ofSize(10);
        if (minNextFloat1 >= maxNextFloat1) {
            return LongStream.empty();
        }
        builder.withLowerBound(0, (long) (minNextFloat1 * 0x1.0p24f) << 24).withUpperBound(0, (long) (maxNextFloat1 * 0x1.0p24f) << 24);
        if (minNextFloat2 >= maxNextFloat2) {
            return LongStream.empty();
        }
        builder.withLowerBound(1, (long) (minNextFloat2 * 0x1.0p24f) << 24).withUpperBound(1, (long) (maxNextFloat2 * 0x1.0p24f) << 24);
        if (minNextFloat3 >= maxNextFloat3) {
            return LongStream.empty();
        }
        builder.withLowerBound(2, (long) (minNextFloat3 * 0x1.0p24f) << 24).withUpperBound(2, (long) (maxNextFloat3 * 0x1.0p24f) << 24);
        if (minNextFloat4 >= maxNextFloat4) {
            return LongStream.empty();
        }
        builder.withLowerBound(3, (long) (minNextFloat4 * 0x1.0p24f) << 24).withUpperBound(3, (long) (maxNextFloat4 * 0x1.0p24f) << 24);
        if (minNextFloat5 >= maxNextFloat5) {
            return LongStream.empty();
        }
        builder.withLowerBound(4, (long) (minNextFloat5 * 0x1.0p24f) << 24).withUpperBound(4, (long) (maxNextFloat5 * 0x1.0p24f) << 24);
        if (minNextFloat6 >= maxNextFloat6) {
            return LongStream.empty();
        }
        builder.withLowerBound(5, (long) (minNextFloat6 * 0x1.0p24f) << 24).withUpperBound(5, (long) (maxNextFloat6 * 0x1.0p24f) << 24);
        if (minNextFloat7 >= maxNextFloat7) {
            return LongStream.empty();
        }
        builder.withLowerBound(6, (long) (minNextFloat7 * 0x1.0p24f) << 24).withUpperBound(6, (long) (maxNextFloat7 * 0x1.0p24f) << 24);
        if (minNextFloat8 >= maxNextFloat8) {
            return LongStream.empty();
        }
        builder.withLowerBound(7, (long) (minNextFloat8 * 0x1.0p24f) << 24).withUpperBound(7, (long) (maxNextFloat8 * 0x1.0p24f) << 24);
        if (minNextFloat9 >= maxNextFloat9) {
            return LongStream.empty();
        }
        builder.withLowerBound(8, (long) (minNextFloat9 * 0x1.0p24f) << 24).withUpperBound(8, (long) (maxNextFloat9 * 0x1.0p24f) << 24);
        if (minNextFloat10 >= maxNextFloat10) {
            return LongStream.empty();
        }
        builder.withLowerBound(9, (long) (minNextFloat10 * 0x1.0p24f) << 24).withUpperBound(9, (long) (maxNextFloat10 * 0x1.0p24f) << 24);
        return EnumerateRt.enumerate(BASIS, ORIGIN, builder.build(), ROOT_INV, ROOT_ORIGIN)
            .mapToLong(vec -> (vec.get(0).getNumerator().longValue() * 0x641598c21879L + 0x60dd589d4b7eL) & ((1L << 48) - 1));
    }
}