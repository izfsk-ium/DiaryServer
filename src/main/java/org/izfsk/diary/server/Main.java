package org.izfsk.diary.server;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.izfsk.diary.server.controllers.Auth;
import org.izfsk.diary.server.storage.Storage;
import org.slf4j.LoggerFactory;

import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import java.io.File;
import java.util.Objects;

public class Main {
	private static Server createHTTP2ServerWithTLSSupport(){
		var server = new Server();

		var connector = new ServerConnector(server);
		connector.setPort(8080);
		server.addConnector(connector);

		// HTTP Configuration
		var httpConfig = new HttpConfiguration();
		httpConfig.setSendServerVersion(false);
		httpConfig.setSecureScheme("https");
		httpConfig.setSecurePort(7777);

		var disableSNICheckForLocalDevelopment = new SecureRequestCustomizer();
		disableSNICheckForLocalDevelopment.setSniHostCheck(false);
		disableSNICheckForLocalDevelopment.setSniRequired(false);
		httpConfig.addCustomizer(disableSNICheckForLocalDevelopment);

		// SSL Context Factory for HTTPS and HTTP/2
		SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
		sslContextFactory.setKeyStorePath(Objects.requireNonNull(Main.class.getResource("/keystore.jks")).toExternalForm()); // replace with your real keystore
		sslContextFactory.setKeyStorePassword("zvrg4kc9"); // replace with your real password
		sslContextFactory.setCipherComparator(HTTP2Cipher.COMPARATOR);
		sslContextFactory.setProvider("Conscrypt");

		// HTTPS Configuration
		var httpsConfig = new HttpConfiguration(httpConfig);
		httpsConfig.addCustomizer(new SecureRequestCustomizer());

		// HTTP/2 Connection Factory
		var h2 = new HTTP2ServerConnectionFactory(httpsConfig);
		ALPNServerConnectionFactory alpnServerConnectionFactory = new ALPNServerConnectionFactory();
		alpnServerConnectionFactory.setDefaultProtocol("h2");

		// SSL Connection Factory
		var ssl = new SslConnectionFactory(sslContextFactory, alpnServerConnectionFactory.getProtocol());

		// HTTP/2 Connector
		var http2Connector = new ServerConnector(server, ssl, alpnServerConnectionFactory, h2, new HttpConnectionFactory(httpsConfig));
		http2Connector.setPort(7777);
		server.addConnector(http2Connector);

		return server;
	}

	public static void main(String[] args) {
		// check diary dir
		File diaryDir = new File(Configure.DiaryRootDir);
		if (! diaryDir.exists() || ! diaryDir.isDirectory()) {
			LoggerFactory.getLogger("Bootstrap").error("The diaryDir " + Configure.DiaryRootDir + " is not a dir!");
			System.exit(1);
		}

		var application = Javalin.create(
				javalinConfig -> {
					javalinConfig.jetty.server(Main::createHTTP2ServerWithTLSSupport);
					javalinConfig.compression.gzipOnly(5);
					javalinConfig.staticFiles.add(staticFiles -> {
						staticFiles.hostedPath = "/";                   // change to host files on a subpart, like '/assets'
						staticFiles.directory = Configure.AssertFilesLocation;              // the directory where your files are located
						staticFiles.location = Location.EXTERNAL;      // Location.CLASSPATH (jar) or Location.EXTERNAL (file system)
					});
				}
		);
		// auth api
		application.get("/auth", Auth::Stage1AuthHandler);
		application.post("/auth", Auth::Stage2AuthHandler);
		// get diary entry api
		application.get("/data/<date>", Storage::EntryGetHandler);
		// set diary entry api
		application.post("/save", Storage::EntrySetHandler);

		application.error(404, context -> {
			LoggerFactory.getLogger("Main").warn(context.ip() + " want to get " + context.path() + " is not found!");
			context.result("The resource is not found in this server.\n\n\n<small>Nginx</small>");
		});
		application.start();
	}
}