package zw.ecocash.forwarder

import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * Sends one email per SMS, from this phone's own Gmail account.
 * Subject "SMS from <originator>" is exactly what the Apps Script already looks for.
 * The original SMS time goes in the body header line and in the Date header.
 */
object Mailer {
    fun send(s: Settings, sms: Sms) {
        val props = Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.port", "465")
            put("mail.smtp.ssl.enable", "true")
            put("mail.smtp.auth", "true")
            put("mail.smtp.connectiontimeout", "20000")
            put("mail.smtp.timeout", "30000")
            put("mail.smtp.writetimeout", "30000")
        }
        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication() = PasswordAuthentication(s.gmail, s.appPassword)
        })
        val m = MimeMessage(session).apply {
            setFrom(InternetAddress(s.gmail))
            setRecipients(Message.RecipientType.TO, InternetAddress.parse(s.toEmail))
            subject = "SMS from ${sms.sender}"
            sentDate = Date(sms.receivedAt)
            setText(Scrub.clean(sms.body), "UTF-8")
            setHeader("X-EcoCash-Line", s.lineName)
        }
        Transport.send(m)
    }
}
