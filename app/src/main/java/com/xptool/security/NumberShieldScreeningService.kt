package com.xptool.security

import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.Connection
import android.util.Log
import android.widget.Toast

class NumberShieldScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // 1. Show a toast to PROVE the service is running
        Toast.makeText(applicationContext, "XPTool: Call Intercepted!", Toast.LENGTH_SHORT).show()

        // Only screen incoming calls
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val phoneNumber = callDetails.handle?.schemeSpecificPart ?: "Unknown Number"
        val verificationStatus = callDetails.callerNumberVerificationStatus

        // 2. Show a toast with the caller's number
        Toast.makeText(applicationContext, "Number: $phoneNumber\nStatus: $verificationStatus", Toast.LENGTH_LONG).show()

        // =====================================================================
        // 🚨 TEST MODE ACTIVE: This will block EVERY call. 
        // Change 'if (true)' back to 'if (verificationStatus == Connection.VERIFICATION_STATUS_FAILED)' 
        // when you are done testing!
        // =====================================================================
        if (verificationStatus == Connection.VERIFICATION_STATUS_FAILED) {
            
            // 3. Build the rejection response
            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSilenceCall(true)
                .setSkipCallLog(false) 
                .setSkipNotification(false) 
                .build()

            // 4. Reject the call
            respondToCall(callDetails, response)

            // 5. Trigger the Cyberpunk Overlay
            OverlayAlertService.showOverlay(
                context = this,
                sender = phoneNumber,
                score = 99, 
                category = "TEST BLOCK",
                flag = "Caller ID verification failed.",
                preview = "This call was automatically blocked by the Network.",
                isCall = true
            )
            
        } else {
            // Allow the call to ring normally
            respondToCall(callDetails, CallResponse.Builder().build())
        }
    }
}