package com.yearcode.readmsgs

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/** 通过 SMTP 发送邮件 */
object MailSender {

    /**
     * 发送一封邮件
     * @throws Exception 任何配置错误/网络失败都会抛异常，由调用方处理
     */
    @Throws(Exception::class)
    fun send(config: AppConfig, subject: String, body: String) {
        require(config.isValid()) { "邮箱配置不完整" }

        val props = Properties()
        props["mail.smtp.host"] = config.smtpHost.trim()
        props["mail.smtp.port"] = config.smtpPort.trim()
        props["mail.smtp.auth"] = "true"
        props["mail.smtp.connectiontimeout"] = "20000"
        props["mail.smtp.timeout"] = "30000"

        when (config.security) {
            MailSecurity.SSL -> props["mail.smtp.ssl.enable"] = "true"
            MailSecurity.TLS -> props["mail.smtp.starttls.enable"] = "true"
            MailSecurity.NONE -> Unit
        }

        val session = Session.getInstance(
            props,
            object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication =
                    PasswordAuthentication(config.username.trim(), config.password)
            }
        )

        val message = MimeMessage(session)
        message.setFrom(InternetAddress(config.fromEmail.trim()))
        val recipients = config.toEmails
            .split(',', '，', ';', '；')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        require(recipients.isNotEmpty()) { "收件人邮箱不能为空" }
        recipients.forEach { message.addRecipient(Message.RecipientType.TO, InternetAddress(it)) }

        message.subject = subject
        message.setText(body)

        Transport.send(message)
    }
}
