package org.apache.iotdb.tool.core.util;

import org.apache.iotdb.tool.core.model.DsTypeEncodeModel;
import org.apache.iotdb.tool.core.model.EncodeCompressAnalysedModel;
import org.apache.iotdb.tsfile.compress.ICompressor;
import org.apache.iotdb.tsfile.encoding.encoder.*;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;
import org.apache.iotdb.tsfile.read.common.BatchData;
import org.apache.iotdb.tsfile.utils.Binary;
import org.apache.iotdb.tsfile.utils.PublicBAOS;
import org.apache.iotdb.tsfile.utils.TsPrimitiveType;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class TsFileEncodeCompressAnalysedUtil {
  private static final CompressionType[] compressTypes =
      new CompressionType[] {
        CompressionType.SNAPPY,
        CompressionType.GZIP,
        CompressionType.LZ4,
        CompressionType.UNCOMPRESSED
      };

  private static final int compressedWeight = 10;

  private static final int compressedSequenceWeight = 10;

  private static final int compressedCostWeight = 20;

  private static final double zeroRate = 0.8;

  public static List<EncodeCompressAnalysedModel> generateEncodeAndCompressAnalysedWithBatchData(
      BatchData batchData) throws IOException {
    //
    DsTypeEncodeModel encodeModel = generateDsTypeEncodeModel(batchData.getDataType());
    if (encodeModel == null) {
      return null;
    }
    List<Encoder> encoders = encodeModel.getEncoders();
    List<PublicBAOS> publicBAOS = encodeModel.getPublicBAOS();
    while (batchData.hasCurrent()) {
      tsPrimitiveTypeEncode(batchData.currentTsPrimitiveType(), encoders, publicBAOS);
      batchData.next();
    }
    return generateEncodeAndCompressAnalysedBase(encodeModel);
  }

  public static List<EncodeCompressAnalysedModel> generateEncodeAndCompressAnalysedWithTsPrimitives(
      TsPrimitiveType[] tsPrimitiveTypes) throws IOException {
    //
    DsTypeEncodeModel encodeModel = generateDsTypeEncodeModel(tsPrimitiveTypes[0].getDataType());
    if (encodeModel == null) {
      return null;
    }
    List<Encoder> encoders = encodeModel.getEncoders();
    List<PublicBAOS> publicBAOS = encodeModel.getPublicBAOS();
    for (int i = 0; i < tsPrimitiveTypes.length; i++) {
      tsPrimitiveTypeEncode(tsPrimitiveTypes[i], encoders, publicBAOS);
    }
    return generateEncodeAndCompressAnalysedBase(encodeModel);
  }

  private static List<EncodeCompressAnalysedModel> generateEncodeAndCompressAnalysedBase(
      DsTypeEncodeModel encodeModel) throws IOException {
    List<PublicBAOS> publicBAOS = encodeModel.getPublicBAOS();
    List<String> encodeNameList = encodeModel.getEncodeNameList();
    List<EncodeCompressAnalysedModel> modelList = new ArrayList<>();
    List<Encoder> encoders = encodeModel.getEncoders();
    for (int i = 0; i < encodeModel.getEncoders().size(); i++) {
      encoders.get(i).flush(publicBAOS.get(i));
    }
    long uncompressSize = publicBAOS.get(0).size();
    for (int i = 0; i < encodeModel.getEncoders().size(); i++) {
      for (int j = 0; j < compressTypes.length; j++) {
        modelList.add(
            generateAnalysedModel(
                compressTypes[j],
                encodeNameList.get(i),
                uncompressSize,
                encodeModel.getTypeName(),
                publicBAOS.get(i)));
      }
    }
    for (PublicBAOS baos : publicBAOS) {
      baos.close();
    }
    return modelList;
  }

  private static void tsPrimitiveTypeEncode(
      TsPrimitiveType tsPrimitiveType, List<Encoder> encoders, List<PublicBAOS> publicBAOS) {
    switch (tsPrimitiveType.getDataType()) {
      case INT64:
        long longValue = tsPrimitiveType.getLong();
        for (int i = 0; i < encoders.size(); i++) {
          encoders.get(i).encode(longValue, publicBAOS.get(i));
        }
        return;
      case INT32:
        int intValue = tsPrimitiveType.getInt();
        for (int i = 0; i < encoders.size(); i++) {
          encoders.get(i).encode(intValue, publicBAOS.get(i));
        }
        return;
      case FLOAT:
        float floatValue = tsPrimitiveType.getFloat();
        for (int i = 0; i < encoders.size(); i++) {
          encoders.get(i).encode(floatValue, publicBAOS.get(i));
        }
        return;
      case DOUBLE:
        double doubleValue = tsPrimitiveType.getDouble();
        for (int i = 0; i < encoders.size(); i++) {
          encoders.get(i).encode(doubleValue, publicBAOS.get(i));
        }
        return;
      case TEXT:
        Binary textValue = tsPrimitiveType.getBinary();
        for (int i = 0; i < encoders.size(); i++) {
          encoders.get(i).encode(textValue, publicBAOS.get(i));
        }
        return;
      default:
        return;
    }
  }

  private static DsTypeEncodeModel generateDsTypeEncodeModel(TSDataType dataType) {

    switch (dataType) {
      case INT64:
        return generateLongEncodeModel(dataType.name());
      case INT32:
        return generateIntEncodeModel(dataType.name());
      case FLOAT:
        return generateFloatEncodeModel(dataType.name());
      case DOUBLE:
        return generateDoubleEncodeModel(dataType.name());
      case TEXT:
        return generateTextEncodeModel(dataType.name());
      default:
        return null;
    }
  }

  private static DsTypeEncodeModel generateIntEncodeModel(String typeName) {
    List<String> encodeNameList = new ArrayList<>();
    encodeNameList.add(TSEncoding.PLAIN.name());
    encodeNameList.add(TSEncoding.GORILLA.name());
    encodeNameList.add(TSEncoding.RLE.name());
    encodeNameList.add(TSEncoding.TS_2DIFF.name());
    PlainEncoder plainEncoder = new PlainEncoder(TSDataType.INT32, 128);
    IntGorillaEncoder gorillaEncoder = new IntGorillaEncoder();
    IntRleEncoder rleEncoder = new IntRleEncoder();
    DeltaBinaryEncoder.IntDeltaEncoder deltaEncoder = new DeltaBinaryEncoder.IntDeltaEncoder();
    List<Encoder> encoders = new ArrayList<>();
    encoders.add(plainEncoder);
    encoders.add(gorillaEncoder);
    encoders.add(rleEncoder);
    encoders.add(deltaEncoder);
    return generateEncodeModel(typeName, encoders, encodeNameList);
  }

  private static DsTypeEncodeModel generateLongEncodeModel(String typeName) {

    List<String> encodeNameList = new ArrayList<>();
    encodeNameList.add(TSEncoding.PLAIN.name());
    encodeNameList.add(TSEncoding.GORILLA.name());
    encodeNameList.add(TSEncoding.RLE.name());
    encodeNameList.add(TSEncoding.TS_2DIFF.name());
    PlainEncoder plainEncoder = new PlainEncoder(TSDataType.INT64, 128);
    LongGorillaEncoder gorillaEncoder = new LongGorillaEncoder();
    LongRleEncoder rleEncoder = new LongRleEncoder();
    DeltaBinaryEncoder.LongDeltaEncoder deltaEncoder = new DeltaBinaryEncoder.LongDeltaEncoder();
    List<Encoder> encoders = new ArrayList<>();
    encoders.add(plainEncoder);
    encoders.add(gorillaEncoder);
    encoders.add(rleEncoder);
    encoders.add(deltaEncoder);
    return generateEncodeModel(typeName, encoders, encodeNameList);
  }

  private static DsTypeEncodeModel generateFloatEncodeModel(String typeName) {
    List<String> encodeNameList = new ArrayList<>();
    encodeNameList.add(TSEncoding.PLAIN.name());
    encodeNameList.add(TSEncoding.GORILLA.name());
    PlainEncoder plainEncoder = new PlainEncoder(TSDataType.FLOAT, 128);
    SinglePrecisionEncoderV2 gorillaEncoder = new SinglePrecisionEncoderV2();
    List<Encoder> encoders = new ArrayList<>();
    encoders.add(plainEncoder);
    encoders.add(gorillaEncoder);
    return generateEncodeModel(typeName, encoders, encodeNameList);
  }

  private static DsTypeEncodeModel generateDoubleEncodeModel(String typeName) {
    List<String> encodeNameList = new ArrayList<>();
    encodeNameList.add(TSEncoding.PLAIN.name());
    encodeNameList.add(TSEncoding.GORILLA.name());
    PlainEncoder plainEncoder = new PlainEncoder(TSDataType.DOUBLE, 128);
    DoublePrecisionEncoderV2 gorillaEncoder = new DoublePrecisionEncoderV2();

    List<Encoder> encoders = new ArrayList<>();
    encoders.add(plainEncoder);
    encoders.add(gorillaEncoder);

    return generateEncodeModel(typeName, encoders, encodeNameList);
  }

  private static DsTypeEncodeModel generateTextEncodeModel(String typeName) {
    List<String> encodeNameList = new ArrayList<>();
    encodeNameList.add(TSEncoding.PLAIN.name());
    encodeNameList.add(TSEncoding.DICTIONARY.name());
    PlainEncoder plainEncoder = new PlainEncoder(TSDataType.TEXT, 128);
    DictionaryEncoder dictionaryEncoder = new DictionaryEncoder();
    List<Encoder> encoders = new ArrayList<>();
    encoders.add(plainEncoder);
    encoders.add(dictionaryEncoder);
    return generateEncodeModel(typeName, encoders, encodeNameList);
  }

  private static DsTypeEncodeModel generateEncodeModel(
      String typeName, List<Encoder> encoders, List<String> encodeNameList) {
    DsTypeEncodeModel model = new DsTypeEncodeModel();
    model.setTypeName(typeName);

    List<PublicBAOS> baos = new ArrayList<>();
    for (int i = 0; i < encoders.size(); i++) {
      baos.add(new PublicBAOS());
    }
    model.setEncodeNameList(encodeNameList);
    model.setEncoders(encoders);
    model.setPublicBAOS(baos);
    return model;
  }

  private static EncodeCompressAnalysedModel generateAnalysedModel(
      CompressionType compressionType,
      String encodeName,
      long originSize,
      String typeName,
      PublicBAOS baos)
      throws IOException {
    ICompressor compressor;
    if (compressionType.equals(CompressionType.SNAPPY)) {
      compressor = new ICompressor.SnappyCompressor();
    } else if (compressionType.equals(CompressionType.GZIP)) {
      compressor = new ICompressor.GZIPCompressor();
    } else if (compressionType.equals(CompressionType.LZ4)) {
      compressor = new ICompressor.IOTDBLZ4Compressor();
    } else {
      compressor = new ICompressor.NoCompressor();
    }
    long startTime = System.nanoTime();
    long compressedSize = compressor.compress(baos.getBuf()).length;
    long compressedCost = System.nanoTime() - startTime;
    EncodeCompressAnalysedModel model = new EncodeCompressAnalysedModel();
    model.setCompressName(compressionType.name());
    model.setCompressedSize(compressedSize);
    model.setUncompressSize(baos.size());
    model.setTypeName(typeName);
    model.setEncodeName(encodeName);
    model.setEncodedSize(baos.size());
    model.setOriginSize(originSize);
    model.setCompressedCost(compressedCost);
    return model;
  }

  /**
   * 压缩率 + 排序 + 耗时排序
   *
   * @param map
   * @return
   */
  public static List<EncodeCompressAnalysedModel> sortedAnalysedModel(Map<String,EncodeCompressAnalysedModel> map) {
    List<EncodeCompressAnalysedModel> sortedCostModels = map.values().stream().sorted(Comparator.comparing(EncodeCompressAnalysedModel::getCompressedCost)).collect(Collectors.toList());
    List<EncodeCompressAnalysedModel> sortedCompressedModels = map.values().stream().sorted(Comparator.comparing(EncodeCompressAnalysedModel::getCompressedSize)).collect(Collectors.toList());
    Map<String, EncodeCompressAnalysedModel> scoresMap = new HashMap<>();
    // 计算压缩得分
    for (int i = 0; i < sortedCompressedModels.size(); i ++) {
      EncodeCompressAnalysedModel model = sortedCompressedModels.get(i);
      double compressedScores = compressedWeight * (1 - (double)model.getCompressedSize()/model.getOriginSize());
      double sequenceScores = 0;
      double rate = (double)i/ sortedCostModels.size();
      if (rate < zeroRate) {
        sequenceScores = compressedSequenceWeight * (1 - rate);
      }
      model.setScores(compressedScores + sequenceScores);
      String key = model.getCompressName() + "-" + model.getEncodeName();
      scoresMap.put(key, model);
    }
    // 计算耗时得分
    for (int i = 0 ; i < sortedCostModels.size(); i++) {
      EncodeCompressAnalysedModel model = sortedCostModels.get(i);
      double sequenceScores = 0;
      double rate = (double)i/ sortedCostModels.size();
      if (rate < zeroRate) {
        sequenceScores = compressedCostWeight * (1 - rate);
      }
      String key = model.getCompressName() + "-" + model.getEncodeName();
      scoresMap.get(key).setScores(scoresMap.get(key).getScores() + sequenceScores);
    }
    return scoresMap.values().stream().sorted(Comparator.comparing(EncodeCompressAnalysedModel::getScores).reversed()).collect(Collectors.toList());
  }
}
