import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:flutter_nfc_reader/flutter_nfc_reader.dart';

void main() => runApp(new MyApp());

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => new _MyAppState();
}

class _MyAppState extends State<MyApp> {
  NfcData _nfcData;

  @override
  void initState() {
    super.initState();
  }

  Future<void> startNFC() async {
    NfcData response;

    setState(() {
      _nfcData = NfcData();
      _nfcData.status = NFCStatus.reading;
    });

    print('NFC: Scan started');

    try {
      print('NFC: Scan readed NFC tag');
      response = await FlutterNfcReader.read;
    } on PlatformException {
      print('NFC: Scan stopped exception');
    }
    final content = response?.content;
    if(content == null){
      print('NFC: No content');
    }else{
      response.content.map((dynamic record) => print(record));
    }
    setState(() {
      _nfcData = response;
    });
  }

  Future<void> stopNFC() async {
    NfcData response;

    try {
      print('NFC: Stop scan by user');
      response = await FlutterNfcReader.stop;
    } on PlatformException {
      print('NFC: Stop scan exception');
      response = NfcData(
        id: '',
        content: null,
        error: 'NFC scan stop exception',
        statusMapper: '',
      );
      response.status = NFCStatus.error;
    }
    setState(() {
      _nfcData = response;
    });
  }

  Future<void> writeNFC() async {
    NfcData response;

    setState(() {
      _nfcData = NfcData();
      _nfcData.status = NFCStatus.writing;
    });

    try{
      print('NFC: write');
      List<String> testRecords = ["20:This is first message", "21:This is second message"];
      response = await FlutterNfcReader.write(testRecords, "20:This is first message");
    } on PlatformException {
      print('NFC: write exception');
      response = NfcData(
        id: '',
        content: null,
        error: 'NFC write exception',
        statusMapper: ''
      );
      response.status = NFCStatus.error;
    }
    setState(() {
      _nfcData = response;
    });
  }

  Future<NFCAvailability> checkAvailability() async {
    return await FlutterNfcReader.checkAvailability;
  }

  @override
  Widget build(BuildContext context) {
    return new MaterialApp(
      home: new Scaffold(
          appBar: new AppBar(
            title: const Text('Plugin example app'),
          ),
          body: new SafeArea(
            top: true,
            bottom: true,
            child: new Center(
              child: ListView(
                children: <Widget>[
                  new SizedBox(
                    height: 10.0,
                  ),
                  new Text(
                    '- NFC Status -\n',
                    textAlign: TextAlign.center,
                  ),
                  new Text(
                    _nfcData != null ? 'Status: ${_nfcData.status}' : '',
                    textAlign: TextAlign.center,
                  ),
                  new Text(
                    _nfcData != null ? 'Identifier: ${_nfcData.id}' : '',
                    textAlign: TextAlign.center,
                  ),
                  new Text(
                    _nfcData != null ? 'Content: ${_nfcData.content != null ? _nfcData.content.join("\n") : ""}' : '',
                    textAlign: TextAlign.center,
                  ),
                  new Text(
                    _nfcData != null ? 'Error: ${_nfcData.error}' : '',
                    textAlign: TextAlign.center,
                  ),
                  new RaisedButton(
                    child: Text('Start NFC'),
                    onPressed: () {
                      startNFC();
                    },
                  ),
                  new RaisedButton(
                    child: Text('Stop NFC'),
                    onPressed: () {
                      stopNFC();
                    },
                  ),
                  new RaisedButton(
                    child: Text('Write NFC'),
                    onPressed: () {
                      writeNFC();
                    },
                  ),
                  new RaisedButton(
                    child: Text('Check Availability'),
                    onPressed: () {
                      checkAvailability().then((data)=> print(data));
                    },
                  ),
                ],
              ),
            ),
          )),
    );
  }
}
