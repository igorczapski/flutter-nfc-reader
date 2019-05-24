package it.matteocrippa.flutternfcreader

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.*
import android.nfc.tech.Ndef
import android.os.Build
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.nio.charset.Charset
import android.nfc.NdefRecord
import java.io.UnsupportedEncodingException


const val PERMISSION_NFC = 1007

class FlutterNfcReaderPlugin(val registrar: Registrar) : MethodCallHandler, NfcAdapter.ReaderCallback {

    private val activity = registrar.activity()

    private var isReading = false
    private var nfcAdapter: NfcAdapter? = null
    private var nfcManager: NfcManager? = null

    private var resulter: Result? = null

    private var kId = "nfcId"
    private var kContent = "nfcContent"
    private var kError = "nfcError"
    private var kStatus = "nfcStatus"

    private var method = "";

    private var recordsToSave = ArrayList<String>();

    private var lastTagReadId: String? = "";

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
        this.method = call.method;
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
            "NfcWrite" -> {
                val records = call.argument<ArrayList<String>>("records");
                if (records != null)
                    this.recordsToSave = records;
                else
                    this.recordsToSave = ArrayList<String>();
                val lastTagId = call.argument<String>("lastTagReadId");
                if(lastTagId != null)
                    this.lastTagReadId = lastTagId
                else
                    this.lastTagReadId = null;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    activity.requestPermissions(
                            arrayOf(Manifest.permission.NFC),
                            PERMISSION_NFC
                    )
                }

                resulter = result

                writeNFC()
                if (!isReading) {
                    val data = mapOf(kId to "", kContent to null, kError to "NFC Hardware not found", kStatus to "error")
                    result.success(data)
                }
            }
            "NfcAvailability" -> {
                if (nfcAdapter == null) {
                    result.success("NFC_UNAVAILABLE")
                } else if (nfcAdapter?.isEnabled == true) {
                    result.success("NFC_AVAILABLE_ON")
                } else {
                    result.success("NFC_AVAILABLE_OFF")
                }
            }
            else -> {
                result.notImplemented()
            }
        }
    }

    private fun writeNFC(): Boolean {
        isReading = if (nfcAdapter?.isEnabled == true) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                nfcAdapter?.enableReaderMode(registrar.activity(), this, READER_FLAGS, null)
            }

            true
        } else {
            false
        }
        return isReading
    }

    private fun startNFC(): Boolean {
        isReading = if (nfcAdapter?.isEnabled == true) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                nfcAdapter?.enableReaderMode(registrar.activity(), this, READER_FLAGS, null)
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
        try {
            if (method == "NfcRead") {
                onReadTag(tag);
            } else if (method == "NfcWrite") {
                onWriteTag(tag);
            }
        } catch (e: android.nfc.TagLostException) {
            e.printStackTrace()
            val data = mapOf(kId to "", kContent to null, kError to "Tag lost", kStatus to "error")
            resulter?.success(data)
        } catch (e: java.io.IOException) {
            e.printStackTrace()
            val data = mapOf(kId to "", kContent to null, kError to "I/O exception", kStatus to "error")
            resulter?.success(data)
        }
    }

    private fun onWriteTag(tag: Tag?) {
        if (this.recordsToSave.size == 0) {
            val data = mapOf(kId to "", kContent to null, kError to "No records to write", kStatus to "error")
            resulter?.success(data)
            return;
        }
        if (tag != null) {
            for (tech in tag.techList) {
                if (tech == Ndef::class.java.name) {
                    val ndef = Ndef.get(tag);
                    ndef.connect();
                    //Read and check whether this is the tag we want to write
                    val recordsBeforeWrite = ndef?.ndefMessage?.records;
                    val payloadListBeforeWrite = handleReadingNdef(ndef);
                    val iter = payloadListBeforeWrite.listIterator();
                    var isTheSameTag = false;
                    for (item in iter) {
                        if(this.lastTagReadId == null){
                            isTheSameTag = false;
                            break;
                        }
                        if(item == this.lastTagReadId){
                            isTheSameTag = true;
                            break;
                        }
                    }
                    if(!isTheSameTag){
                        val data = mapOf(kId to "", kContent to null, kError to "Cannot write to different tag", kStatus to "error_diff_tag")
                        resulter?.success(data);
                        ndef.close();
                        return;
                    }
                    this.lastTagReadId = null;

                    this.eraseTag(ndef);
                    val recordsForNdefMessage = this.recordsToSave.map {
                        createTextRecord("en", it);
                    }.toTypedArray();
                    val newMessage = NdefMessage(recordsForNdefMessage)
                    ndef.writeNdefMessage(newMessage);

                    //reading confirmation
                    val records = ndef?.ndefMessage?.records;
                    val payloadList = handleReadingNdef(ndef);

                    val id = bytesToHexString(tag?.id) ?: ""
                    ndef.close();
                    if (payloadList.size > 0) {
                        val data = mapOf(kId to id, kContent to payloadList, kError to "", kStatus to "finishedwrite")
                        resulter?.success(data)
                    }else{
                        val data = mapOf(kId to "", kContent to null, kError to "Data not written properly", kStatus to "error")
                        resulter?.success(data)
                    }
                }
            }
        }
    }

    private fun eraseTag(ndef: Ndef?) {
        ndef?.writeNdefMessage(NdefMessage(NdefRecord(NdefRecord.TNF_EMPTY, null, null, null)));
    }

    private fun handleReadingNdef(ndef: Ndef?): MutableList<String> {
        val records = ndef?.ndefMessage?.records;
        var payloadList = mutableListOf<String>();
        if(records == null){
            val data = mapOf(kId to "", kContent to payloadList, kError to "", kStatus to "read");
            resulter?.success(data)
            ndef?.close()
            return payloadList;
        }
        for(record in records){
            val payloadBytes = record?.payload
            if(payloadBytes == null){
                return payloadList;
            }
            val isUTF8 = payloadBytes.get(0).toInt().and(0x080) == 0  //status byte: bit 7 indicates encoding (0 = UTF-8, 1 = UTF-16)
            val languageLength = payloadBytes.get(0).toInt().and(0x03F)     //status byte: bits 5..0 indicate length of language code
            val textLength = payloadBytes.size.minus(1).minus(if (languageLength == null) 0 else languageLength)
            val languageCode = String(payloadBytes, 1, languageLength, Charset.forName("US-ASCII"))
            val encoding = if (isUTF8) Charset.forName("UTF-8") else Charset.forName("UTF-16");
            val payloadText = String(payloadBytes, 1 + languageLength, textLength, encoding)
            payloadList.add(payloadText);
        }
        return payloadList;
    }

    fun createTextRecord(language: String, text: String): NdefRecord {
        val languageBytes: ByteArray
        val textBytes: ByteArray
        try {
            languageBytes = language.toByteArray(charset("US-ASCII"))
            textBytes = text.toByteArray(charset("UTF-8"))
        } catch (e: UnsupportedEncodingException) {
            throw AssertionError(e)
        }

        val recordPayload = ByteArray(1 + (languageBytes.size and 0x03F) + textBytes.size)

        recordPayload[0] = (languageBytes.size and 0x03F).toByte()
        System.arraycopy(languageBytes, 0, recordPayload, 1, languageBytes.size and 0x03F)
        System.arraycopy(textBytes, 0, recordPayload, 1 + (languageBytes.size and 0x03F), textBytes.size)

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, null, recordPayload)
    }


    private fun onReadTag(tag: Tag?) {
        // convert tag to NDEF tag
        val ndef = Ndef.get(tag)
        // ndef will be null if the discovered tag is not a NDEF tag
        // read NDEF message
        ndef?.connect()
        val records = ndef?.ndefMessage?.records;
        val payloadList = handleReadingNdef(ndef);
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