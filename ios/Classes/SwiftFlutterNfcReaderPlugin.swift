import Flutter
import Foundation
import CoreNFC

@available(iOS 11.0, *)
public class SwiftFlutterNfcReaderPlugin: NSObject, FlutterPlugin {
    
    fileprivate var nfcSession: NFCNDEFReaderSession? = nil
    fileprivate var instruction: String? = nil
    fileprivate var resulter: FlutterResult? = nil

    fileprivate let kId = "nfcId"
    fileprivate let kContent = "nfcContent"
    fileprivate let kStatus = "nfcStatus"
    fileprivate let kError = "nfcError"

    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "flutter_nfc_reader", binaryMessenger: registrar.messenger())
        let instance = SwiftFlutterNfcReaderPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch(call.method) {
        case "NfcRead":
            let map = call.arguments as? Dictionary<String, String>
            instruction = map?["instruction"] ?? ""
            resulter = result
            activateNFC(instruction)
        case "NfcStop":
            disableNFC()
        case "NfcWrite":
            let data = [kId: "", kContent: nil, kError: "This method is not supported by iOS", kStatus: "error"]
            resulter?(data)
        default:
            result("iOS " + UIDevice.current.systemVersion)
        }
    }
}

// MARK: - NFC Actions
@available(iOS 11.0, *)
extension SwiftFlutterNfcReaderPlugin {
    func activateNFC(_ instruction: String?) {
        // setup NFC session
        nfcSession = NFCNDEFReaderSession(delegate: self, queue: DispatchQueue(label: "queueName", attributes: .concurrent), invalidateAfterFirstRead: true)
        
        // then setup a new session
        if let instruction = instruction {
            nfcSession?.alertMessage = instruction
        }
        
        // start
        if let nfcSession = nfcSession {
            nfcSession.begin()
        }
    }
    
    func disableNFC() {
        nfcSession?.invalidate()
        let data = [kId: "", kContent: nil, kError: "", kStatus: "stopped"]

        resulter?(data)
        resulter = nil
    }

}

// MARK: - NFCDelegate
@available(iOS 11.0, *)
extension SwiftFlutterNfcReaderPlugin : NFCNDEFReaderSessionDelegate {
    
    public func readerSession(_ session: NFCNDEFReaderSession, didDetectNDEFs messages: [NFCNDEFMessage]) {
        guard let message = messages.first else { return }
        var records = message.records;
        var payloadList:[String] = [];
        for record in records {
            var typeString = String(data: record.type, encoding: String.Encoding.utf8);
            var idString = String(data: record.identifier, encoding: String.Encoding.utf8);
            var payloadBytesLength = record.payload.count;
            if(typeString == nil || idString == nil){ //TODO check if payload exists?
                return ;
            }
            var payloadBytes = record.payload.withUnsafeBytes{ (bytes: UnsafePointer<UInt8>) in Array(UnsafeBufferPointer(start: bytes, count: record.payload.count / MemoryLayout<UInt8>.stride)) }
            if(record.typeNameFormat == NFCTypeNameFormat.nfcWellKnown) {
                if(typeString == "T"){
                    var payloadToAppend = parseTextPayload(payloadBytes: payloadBytes, length: payloadBytesLength);
                    if(payloadToAppend != nil){
                        payloadList.append(payloadToAppend!);
                    }
                }
            }
        }
        let data: [String:Any] = [kId: "", kContent: payloadList, kError: "", kStatus: "read"]

        resulter?(data)
        disableNFC()
    }
    
    private func parseTextPayload(payloadBytes: [UInt8], length: Int) -> String? {
        if length < 1 {
            return nil
        }
        // Parse first byte Text Record Status Byte.
        let isUTF16 = (Int(payloadBytes[0]) & 0x080) == 0
        let codeLength: Int = Int(payloadBytes[0]) & 0x7f
        
        if length < Int(1 + codeLength) {
            return nil
        }
        
        // Get lang code and text.
        let langCode = String(bytes: payloadBytes[1...codeLength], encoding: String.Encoding.utf8);
        let textStartingPos = 1+codeLength;
        let text = String(bytes: payloadBytes[textStartingPos...payloadBytes.count-1], encoding: String.Encoding.utf8);
        if langCode == nil || text == nil {
            return nil
        }
        //TODO make use of lang code
        return text;
    }
    
    public func readerSession(_ session: NFCNDEFReaderSession, didInvalidateWithError error: Error) {
        print(error.localizedDescription)
        let data = [kId: "", kContent: nil, kError: error.localizedDescription, kStatus: "error"]

        resulter?(data)
        disableNFC()
    }
}
