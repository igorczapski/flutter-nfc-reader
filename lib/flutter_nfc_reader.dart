import 'dart:async';
import 'package:flutter/services.dart';

enum NFCStatus {
  none,
  reading,
  writing,
  finishedwrite,
  read,
  stopped,
  error,
}

class NfcData {
  final String id;
  final List<dynamic> content;
  final String error;
  final String statusMapper;

  NFCStatus status;

  NfcData({
    this.id,
    this.content,
    this.error,
    this.statusMapper,
  });

  factory NfcData.fromMap(Map data) {
    NfcData result = NfcData(
      id: data['nfcId'],
      content: data['nfcContent'],
      error: data['nfcError'],
      statusMapper: data['nfcStatus'],
    );
    switch (result.statusMapper) {
      case 'none':
        result.status = NFCStatus.none;
        break;
      case 'reading':
        result.status = NFCStatus.reading;
        break;
      case 'stopped':
        result.status = NFCStatus.stopped;
        break;
      case 'error':
        result.status = NFCStatus.error;
        break;
      case 'writing':
        result.status = NFCStatus.writing;
        break;
      case 'finishedwrite':
        result.status = NFCStatus.finishedwrite;
        break;
      default:
        result.status = NFCStatus.none;
    }
    return result;
  }
}

class FlutterNfcReader {
  static const MethodChannel _channel =
      const MethodChannel('flutter_nfc_reader');

  static Future<NfcData> get read async {
    final Map data = await _channel.invokeMethod('NfcRead');

    final NfcData result = NfcData.fromMap(data);

    return result;
  }

  static Future<NfcData> get stop async {
    final Map data = await _channel.invokeMethod('NfcStop');

    final NfcData result = NfcData.fromMap(data);

    return result;
  }

  static Future<NfcData> write(List<dynamic> nfcRecords) async {
    final Map data = await _channel.invokeMethod('NfcWrite', <String, dynamic>{
      'records': nfcRecords
    });
    final NfcData result = NfcData.fromMap(data);
    return result;
  }
}
