package cn.kk.extractor.lingoes;

/* Adapted from https://github.com/PurlingNayuki/lingoes-extractor
 * Original by Xiaoyun Zhu, Copyright (c) 2010 MIT Licence
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cn.kk.extractor.lingoes.ArrayHelper.SensitiveStringDecoder;

import jijimaku.errors.UnexpectedCriticalError;
import jijimaku.utils.FileManager;

public class LingoesLd2Extractor {
  private static final Logger LOGGER;
  private static final ArrayHelper.SensitiveStringDecoder[] AVAIL_ENCODINGS = {
      new ArrayHelper.SensitiveStringDecoder(Charset.forName("UTF-8")),
      new ArrayHelper.SensitiveStringDecoder(Charset.forName("UTF-16LE")),
      new ArrayHelper.SensitiveStringDecoder(Charset.forName("UTF-16BE")),
      new ArrayHelper.SensitiveStringDecoder(Charset.forName("EUC-JP"))
  };
  private static final byte[] TRANSFER_BYTES = new byte[Helper.BUFFER_SIZE];

  static {
    System.setProperty("logDir", FileManager.getLogsDirectory());
    LOGGER = LogManager.getLogger();
  }

  public LingoesLd2Extractor() {
  }

  private static String strip(String xml) {
    int open;
    int end;
    if ((open = xml.indexOf("<![CDATA[")) != -1) {
      if ((end = xml.indexOf("]]>", open)) != -1) {
        return xml.substring(open + "<![CDATA[".length(), end).replace('\t', ' ')
            .replace(Helper.SEP_NEWLINE_CHAR, ' ').replace('\u001e', ' ').replace('\u001f', ' ');
      }
    } else if ((open = xml.indexOf("<Ô")) != -1) {
      if ((end = xml.indexOf("</Ô", open)) != -1) {
        open = xml.indexOf(">", open + 1);
        return xml.substring(open + 1, end).replace('\t', ' ').replace(Helper.SEP_NEWLINE_CHAR, ' ')
            .replace('\u001e', ' ').replace('\u001f', ' ');
      }
    } else {
      StringBuilder sb = new StringBuilder();
      end = 0;
      open = xml.indexOf('<');
      do {
        if ((open - end) > 1) {
          sb.append(xml.substring(end + 1, open));
        }
        open = xml.indexOf('<', open + 1);
        end = xml.indexOf('>', end + 1);
      } while ((open != -1) && (end != -1));
      return sb.toString().replace('\t', ' ').replace(Helper.SEP_NEWLINE_CHAR, ' ').replace('\u001e', ' ')
          .replace('\u001f', ' ');
    }
    return Helper.EMPTY_STRING;
  }

  private static void getIdxData(final ByteBuffer dataRawBytes, final int position, final int[] wordIdxData) {
    dataRawBytes.position(position);
    wordIdxData[0] = dataRawBytes.getInt();
    wordIdxData[1] = dataRawBytes.getInt();
    wordIdxData[2] = dataRawBytes.get() & 0xff;
    wordIdxData[3] = dataRawBytes.get() & 0xff;
    wordIdxData[4] = dataRawBytes.getInt();
    wordIdxData[5] = dataRawBytes.getInt();
  }

  private static ByteBuffer inflate(final ByteBuffer dataRawBytes, final List<Integer> deflateStreams)
      throws IOException {
    int startOffset = dataRawBytes.position();
    int offset;
    int lastOffset = startOffset;
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    for (Integer offsetRelative : deflateStreams) {
      offset = startOffset + offsetRelative;
      LingoesLd2Extractor.decompress(out, dataRawBytes, lastOffset, offset - lastOffset);
      lastOffset = offset;
    }
    return ByteBuffer.wrap(out.toByteArray());
  }

  private static long decompress(final ByteArrayOutputStream out, final ByteBuffer data, final int offset,
                                 final int length) throws IOException {
    Inflater inflator = new Inflater();
    long bytesRead;
    try (InflaterInputStream in = new InflaterInputStream(new ByteArrayInputStream(data.array(), offset, length),
        inflator, Helper.BUFFER_SIZE)) {
      LingoesLd2Extractor.writeInputStream(in, out);
      bytesRead = inflator.getBytesRead();
      inflator.end();
    }
    return bytesRead;
  }

  private static void writeInputStream(final InputStream in, final OutputStream out) throws IOException {
    int len;
    while ((len = in.read(LingoesLd2Extractor.TRANSFER_BYTES)) > 0) {
      out.write(LingoesLd2Extractor.TRANSFER_BYTES, 0, len);
    }
  }

  public Map<String, String> extractLd2ToMap(File ld2File) throws IOException {
    // read lingoes ld2 into byte array
    ByteBuffer dataRawBytes;
    try (FileChannel fChannel = new RandomAccessFile(ld2File, "r").getChannel()) {
      dataRawBytes = ByteBuffer.allocate((int) fChannel.size());
      fChannel.read(dataRawBytes);
    }
    dataRawBytes.order(ByteOrder.LITTLE_ENDIAN);
    dataRawBytes.rewind();

    int offsetData = dataRawBytes.getInt(0x5C) + 0x60;
    if (dataRawBytes.limit() > offsetData) {
      int type = dataRawBytes.getInt(offsetData);
      int offsetWithInfo = dataRawBytes.getInt(offsetData + 4) + offsetData + 12;
      if (type == 3) {
        return readDictionary(dataRawBytes, offsetData);
      } else if (dataRawBytes.limit() > (offsetWithInfo + 0x1C)) {
        return readDictionary(dataRawBytes, offsetWithInfo);
      }
    }
    LOGGER.error("Cannot find LD2 dictionary data");
    throw new UnexpectedCriticalError();
  }

  private Map<String, String> readDictionary(ByteBuffer dataRawBytes, int offsetData) throws IOException {
    int limit = dataRawBytes.getInt(offsetData + 4) + offsetData + 8;
    int offsetIndex = offsetData + 0x1C;
    int offsetCompressedDataHeader = dataRawBytes.getInt(offsetData + 8) + offsetIndex;
    int inflatedWordsIndexLength = dataRawBytes.getInt(offsetData + 12);
    int inflatedWordsLength = dataRawBytes.getInt(offsetData + 16);
    List<Integer> deflateStreams = new ArrayList<>();
    dataRawBytes.position(offsetCompressedDataHeader + 8);
    int offset = dataRawBytes.getInt();
    while ((offset + dataRawBytes.position()) < limit) {
      offset = dataRawBytes.getInt();
      deflateStreams.add(offset);
    }
    ByteBuffer inflatedBytes = LingoesLd2Extractor.inflate(dataRawBytes, deflateStreams);

    return extract(inflatedBytes, inflatedWordsIndexLength, inflatedWordsIndexLength + inflatedWordsLength);
  }

  private Map<String, String> extract(ByteBuffer inflatedBytes, int offsetDefs, int offsetXml) throws IOException {
    Map<String, String> definitions = new HashMap<>();
    inflatedBytes.order(ByteOrder.LITTLE_ENDIAN);

    final int dataLen = 10;
    final int defTotal = (offsetDefs / dataLen) - 1;

    int[] idxData = new int[6];
    String[] defData = new String[2];

    final SensitiveStringDecoder[] encodings = detectEncodings(inflatedBytes, offsetDefs,
        offsetXml, defTotal, dataLen, idxData, defData);

    inflatedBytes.position(8);
    int failCounter = 0;
    for (int i = 0; i < defTotal; i++) {
      readDefinitionData(inflatedBytes, offsetDefs, offsetXml, dataLen, encodings[0], encodings[1], idxData,
          defData, i);

      defData[0] = defData[0].trim();
      defData[1] = defData[1].trim();

      if (defData[0].isEmpty() || defData[1].isEmpty()) {
        failCounter++;
      }
      if (failCounter > (defTotal * 0.01)) {
        LOGGER.error("LD2 entry problem: " + defData[0] + " = " + defData[1]);
      }

      definitions.put(defData[0], defData[1]);
    }
    return definitions;
  }

  private ArrayHelper.SensitiveStringDecoder[] detectEncodings(final ByteBuffer inflatedBytes,
                                                               final int offsetWords, final int offsetXml, final int defTotal, final int dataLen,
                                                               final int[] idxData, final String[] defData) throws UnsupportedEncodingException {
    final int test = Math.min(defTotal, 500);
    for (SensitiveStringDecoder element : LingoesLd2Extractor.AVAIL_ENCODINGS) {
      for (SensitiveStringDecoder element2 : LingoesLd2Extractor.AVAIL_ENCODINGS) {
        try {
          readDefinitionData(inflatedBytes, offsetWords, offsetXml, dataLen, element, element2, idxData,
              defData, test);
          LOGGER.debug("LD2 words encoding: " + element.name);
          LOGGER.debug("LD2 definitions encoding: " + element2.name);
          return new ArrayHelper.SensitiveStringDecoder[] {
              element, element2
          };
        } catch (Throwable e) {
          // ignore
        }
      }
    }
    LOGGER.error("Failed to detect LD2 dictionary encoding");
    throw new UnexpectedCriticalError();
  }

  private void readDefinitionData(final ByteBuffer inflatedBytes, final int offsetWords, final int offsetXml,
                                  final int dataLen, final ArrayHelper.SensitiveStringDecoder wordDecoder,
                                  final ArrayHelper.SensitiveStringDecoder valueDecoder, final int[] wordIdxData, final String[] wordData,
                                  final int idx) throws UnsupportedEncodingException {
    LingoesLd2Extractor.getIdxData(inflatedBytes, dataLen * idx, wordIdxData);
    int lastWordPos = wordIdxData[0];
    int lastXmlPos = wordIdxData[1];
    int refs = wordIdxData[3];
    int currentWordOffset = wordIdxData[4];
    int currenXmlOffset = wordIdxData[5];
    String xml = LingoesLd2Extractor.strip(new String(valueDecoder.decode(inflatedBytes.array(), offsetXml
        + lastXmlPos, currenXmlOffset - lastXmlPos)));

    while (refs-- > 0) {
      int ref = inflatedBytes.getInt(offsetWords + lastWordPos);
      LingoesLd2Extractor.getIdxData(inflatedBytes, dataLen * ref, wordIdxData);
      lastXmlPos = wordIdxData[1];
      currenXmlOffset = wordIdxData[5];
      if (xml.isEmpty()) {
        xml = LingoesLd2Extractor.strip(new String(valueDecoder.decode(inflatedBytes.array(), offsetXml
            + lastXmlPos, currenXmlOffset - lastXmlPos)));
      } else {
        xml = LingoesLd2Extractor.strip(new String(valueDecoder.decode(inflatedBytes.array(), offsetXml
            + lastXmlPos, currenXmlOffset - lastXmlPos)))
            + Helper.SEP_LIST + xml;
      }
      lastWordPos += 4;
    }
    wordData[1] = xml;

    String word = new String(wordDecoder.decode(inflatedBytes.array(), offsetWords + lastWordPos, currentWordOffset
        - lastWordPos));
    wordData[0] = word;
  }
}
