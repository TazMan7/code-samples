
@Grapes([
    @Grab(group='com.swmansion.starknet', module='starknet', version='0.7.3'),
    @Grab(group='org.testcontainers', module='spock', version='1.19.1'),
    @Grab(group='org.apache.commons', module='commons-math3', version='3.6.1'),
    @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7')
])
package com.paradex.api.rest

import com.swmansion.starknet.data.TypedData
import com.swmansion.starknet.data.types.Felt
import spock.lang.Specification
import org.apache.commons.math3.util.BigReal
import com.swmansion.starknet.signer.StarkCurveSigner
import java.lang.reflect.Method
import java.util.List
import java.util.stream.Collectors
import groovyx.net.http.RESTClient
import java.net.URL
import groovyx.net.http.*
import groovy.json.JsonBuilder

class ParadexRequestFactoryTest extends Specification {

    def "GetAuthRequest"() {
        when:
        // long timestamp = 1699960992L;
        // long expiration = timestamp + 24 * 60 * 60; // Expires in 30 minutes.
        long now = System.currentTimeMillis()
        long expiry= now + 24 * 60 * 60;
        BigInteger chainID = new BigInteger("7693264728749915528729180568779831130134670232771119425")
        String hexValue = "0x" + chainID.toString(16).toUpperCase()
        println("Chain ID (BigInteger): $chainID")
        println("Chain ID (hex): $hexValue")
        def authMsg = createAuthMessage(now, expiry, hexValue)
        println(authMsg);
        //Replace with your L2 Address
        Felt accountAddress = Felt.fromHex("0x6b55e29c69801bc22bbfc9f651beb6017d0e7f25c4b1af3ecf7da4c62f3201")
        TypedData typedData = TypedData.fromJsonString(authMsg);
        StarkCurveSigner sign_tx=new StarkCurveSigner(accountAddress)
        println(sign_tx)
        List<Felt> sig_val= sign_tx.signTypedData(typedData, accountAddress)
        println(convertSig(sig_val))
        String hex_val_sig = signMessage(accountAddress, authMsg)
        def apiUrl = 'https://api.testnet.paradex.trade/v1/auth/'
        def requestHeaders = [
            "PARADEX-STARKNET-ACCOUNT": accountAddress.toString(),
            "PARADEX-STARKNET-SIGNATURE": convertSig(sig_val),
            "PARADEX-STARKNET-MESSAGE-HASH": hex_val_sig.toString(),
            "PARADEX-TIMESTAMP": now.toString(),
            "PARADEX-SIGNATURE-EXPIRATION": expiry.toString(),
        ]

        makeRestAPICall(apiUrl, requestHeaders)

        then:
        String hex_val = signMessage(accountAddress, authMsg)
        hex_val == "0x1a22395dd17dc414ccefa7eb6e59bc1ba14cdc2a869b3e6e22237b397d6551"
    }

    void makeRestAPICall(String url, Map headers) {
        def connection = new URL(url).openConnection()
        connection.requestMethod = 'POST'
        connection.doOutput = true
        connection.setRequestProperty('Content-Type', 'application/json')

        // Set headers
        headers.each { key, value ->
            connection.setRequestProperty(key, value)
        }
        def postBody = []
        def jsonBody = new JsonBuilder(postBody).toString()

        // Write the JSON body to the request
        connection.outputStream.withWriter { writer ->
            writer.write(jsonBody)
        }

        // Read the response
        def responseCode = connection.responseCode
        def responseData = connection.inputStream.text

        println "Response Status: ${responseCode}"
        println "Response Data: ${responseData}"
    }

    String convertSig(List<BigInteger> sig) {
        // Convert the sig list to a JSON array string
        def jsonArray = sig.collect { "\"${it}\"" }.join(',')
        return "[$jsonArray]"
    }

    String signMessage(Felt accountAddress, String jsonMessage) {
        TypedData typedData = TypedData.fromJsonString(jsonMessage);
        Felt messageHash = typedData.getMessageHash(accountAddress);
        return messageHash.hexString();
    }

    static String createAuthMessage(long timestamp, long expiration, String chainIdHex) {
        println(timestamp);
        println(expiration);
        println(chainIdHex)
        return """
           {
                "message": {
                    "method": "POST",
                    "path": "/v1/auth",
                    "body": "",
                    "timestamp": %s,
                    "expiration": %s
                },
                "domain": {"name": "Paradex", "chainId": "%s", "version": "1"},
                "primaryType": "Request",
                "types": {
                    "StarkNetDomain": [
                        {"name": "name", "type": "felt"},
                        {"name": "chainId", "type": "felt"},
                        {"name": "version", "type": "felt"}
                    ],
                    "Request": [
                        {"name": "method", "type": "felt"},
                        {"name": "path", "type": "felt"},
                        {"name": "body", "type": "felt"},
                        {"name": "timestamp", "type": "felt"},
                        {"name": "expiration", "type": "felt"}
                    ]
                }
            }
            """.formatted(timestamp, expiration, chainIdHex);
    }
}
