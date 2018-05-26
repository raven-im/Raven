package protobuf.analysis;

import com.google.protobuf.MessageLite;
import java.io.IOException;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ParseMap {

    private static final Logger logger = LoggerFactory.getLogger(ParseMap.class);

    public static HashMap<Integer, ParseMap.Parsing> parseMap = new HashMap<>();
    public static HashMap<Class<?>, Integer> msg2ptoNum = new HashMap<>();

    @FunctionalInterface
    public interface Parsing {

        MessageLite process(byte[] bytes) throws IOException;
    }


    public static void register(int ptoNum, ParseMap.Parsing parse, Class<?> cla) {
        if (parseMap.get(ptoNum) == null) {
            parseMap.put(ptoNum, parse);
        } else {
            logger.error("pto has been registered in parseMap, ptoNum: {}", ptoNum);
            return;
        }

        if (msg2ptoNum.get(cla) == null) {
            msg2ptoNum.put(cla, ptoNum);
        } else {
            logger.error("pto has been registered in msg2ptoNum, ptoNum: {}", ptoNum);
        }
    }

    public static MessageLite getMessage(int ptoNum, byte[] bytes) throws IOException {
        Parsing parser = parseMap.get(ptoNum);
        if (parser == null) {
            logger.error("UnKnown Protocol Num: {}", ptoNum);
        }
        MessageLite msg = parser.process(bytes);

        return msg;
    }

    public static Integer getPtoNum(MessageLite msg) {
        return getPtoNum(msg.getClass());
    }

    public static Integer getPtoNum(Class<?> clz) {
        return msg2ptoNum.get(clz);
    }

}
