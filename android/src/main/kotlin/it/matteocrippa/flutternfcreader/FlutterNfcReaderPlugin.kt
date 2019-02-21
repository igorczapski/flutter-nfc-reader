package it.matteocrippa.flutternfcreader

import android.Manifest
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.NfcManager
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Build
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.nio.charset.Charset


const val PERMISSION_NFC = 1007

class FlutterNfcReaderPlugin(val registrar: Registrar) : MethodCallHandler,  NfcAdapter.ReaderCallback {

    private val activity = registrar.activity()

    private var isReading = false
    private var nfcAdapter: NfcAdapter? = null
    private var nfcManager: NfcManager? = null

    private var resulter: Result? = null

    private var kId = "nfcId"
    private var kContent = "nfcContent"
    private var kError = "nfcError"
    private var kStatus = "nfcStatus"

    private var READER_FLAGS = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_V;

    companion object {
        @JvmStatic
        fun registerWith(registrar: Registrar): Unit {
            val channel = MethodChannel(registrar.messenger(), "flutter_nfc_reader")
            channel.setMethodCallHandler(FlutterNfcReaderPlugin(registrar))
        }
    }

    init {
        nfcManager = activity.getSystemService(Context.NFC_SERVICE) as? NfcManager
        nfcAdapter = nfcManager?.defaultAdapter
    }

    override fun onMethodCall(call: MethodCall, result: Result): Unit {

        when (call.method) {
            "NfcRead" -> {

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.requestPermissions(
                        arrayOf(Manifest.permission.NFC),
                        PERMISSION_NFC
                    )
                }

                resulter = result
                startNFC()

                if (!isReading) {
                    val data = mapOf(kId to "", kContent to null, kError to "NFC Hardware not found", kStatus to "error")
                    result.success(data)
                    resulter = null
                }

            }
            "NfcStop" -> {
                stopNFC()
                val data = mapOf(kId to "", kContent to null, kError to "", kStatus to "stopped")
                result.success(data)
            }
            //TODO handle writes
//            "NfcWrite" -> {
//                result.success(null)
//            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun startNFC(): Boolean {
        isReading = if (nfcAdapter?.isEnabled == true) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                nfcAdapter?.enableReaderMode(registrar.activity(), this, READER_FLAGS, null )
            }

            true
        } else {
            false
        }
        return isReading
    }

    private fun stopNFC() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            nfcAdapter?.disableReaderMode(registrar.activity())
        }
        resulter = null
        isReading = false
    }

    // handle discovered NDEF Tags
    override fun onTagDiscovered(tag: Tag?) {
        // convert tag to NDEF tag
        val ndef = Ndef.get(tag)
        // ndef will be null if the discovered tag is not a NDEF tag
        // read NDEF message
        ndef?.connect()
        val records = ndef?.ndefMessage?.records;

        if(records == null){
            ndef?.close()
            return;
        }
        var payloadList = mutableListOf<String>();
        for(record in records){
            val payloadBytes = record?.payload
            if(payloadBytes == null){
                return;
            }
            val isUTF8 = payloadBytes.get(0).toInt().and(0x080) == 0  //status byte: bit 7 indicates encoding (0 = UTF-8, 1 = UTF-16)
            val languageLength = payloadBytes.get(0).toInt().and(0x03F)     //status byte: bits 5..0 indicate length of language code
            val textLength = payloadBytes.size.minus(1).minus(if (languageLength == null) 0 else languageLength)
            val languageCode = String(payloadBytes, 1, languageLength, Charset.forName("US-ASCII"))
            val encoding = if (isUTF8) Charset.forName("UTF-8") else Charset.forName("UTF-16");
            val payloadText = String(payloadBytes, 1 + languageLength, textLength, encoding)
            payloadList.add(payloadText);
        }
        val id = bytesToHexString(tag?.id) ?: ""
        ndef?.close()
        if (payloadList.size > 0) {
            val data = mapOf(kId to id, kContent to payloadList, kError to "", kStatus to "read")
            resulter?.success(data)
        }
    }

    private fun bytesToHexString(src: ByteArray?): String? {
        val stringBuilder = StringBuilder("0x")
        if (src == null || src.isEmpty()) {
            return null
        }

        val buffer = CharArray(2)
        for (i in src.indices) {
            buffer[0] = Character.forDigit(src[i].toInt().ushr(4).and(0x0F), 16)
            buffer[1] = Character.forDigit(src[i].toInt().and(0x0F), 16)
            stringBuilder.append(buffer)
        }

        return stringBuilder.toString()
    }
}