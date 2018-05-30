package be.nabu.libs.resources.sftp;

import java.io.IOException;
import java.net.URI;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;

import be.nabu.libs.authentication.api.principals.BasicPrincipal;
import be.nabu.libs.authentication.impl.AuthenticationUtils;
import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceResolver;

public class SftpResourceResolver implements ResourceResolver {

	private static List<String> defaultSchemes = Arrays.asList(new String[] { "sftp" });
	
	@Override
	public Resource getResource(URI uri, Principal principal) throws IOException {
		if (principal == null) {
			principal = AuthenticationUtils.toPrincipal(uri);
		}
		JSch jsch = new JSch();
		
		Session session = null;
		ChannelSftp channel = null;
		try {
			// TODO: need better key support, perhaps get it from keystore? https://stackoverflow.com/questions/31385944/how-to-add-ssh-identity-file-keypair-to-jks-keystore?utm_medium=organic&utm_source=google_rich_qa&utm_campaign=google_rich_qa
			// or can add the priv/pub key to the repository somewhere and read those?
			// need to capture pass for priv key as well
			// can support resource-based resolving of keystore
			// add keystore password + alias for key
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			if (queryProperties != null && queryProperties.get("key") != null) {
				for (String key : queryProperties.get("key")) {
					jsch.addIdentity(key);
				}
			}
			
			session = jsch.getSession(
				principal.getName(), 
				uri.getHost(), 
				uri.getPort() < 0 ? 22 : uri.getPort());

			// set password if applicable
			if (principal instanceof BasicPrincipal) {
				String password = ((BasicPrincipal) principal).getPassword();
				if (password != null) {
					session.setPassword(password);
				}
			}
			
			session.setConfig("StrictHostKeyChecking", "no");

			session.connect();

			// set to 5 minutes
			session.setServerAliveInterval(300000);
			
			channel = (ChannelSftp) session.openChannel("sftp");
			channel.connect();
			
			try {
				// if we have user info, strip it from the URI when passing it along, otherwise it will end up everywhere
				// also strip query parameters
				if (uri.getUserInfo() != null) {
					uri = new URI(uri.getScheme(), uri.getHost() + (uri.getPort() < 0 ? "" : ":" + uri.getPort()), uri.getPath(), null, null);
				}
				if (uri.getPath() != null && !uri.getPath().equals("/")) {
					// skip the absolute leading slash
					String path = uri.getPath().substring(1);
					SftpATTRS lstat = channel.lstat(path);
					if (lstat != null) {
						if (lstat.isDir()) {
							return new SftpDirectory(null, channel, session, uri, lstat);
						}
						else {
							return new SftpItem(null, channel, session, uri, lstat);
						}
					}
				}
				else {
					// get your login directory stats
					SftpATTRS lstat = channel.lstat(".");
					if (lstat != null) {
						return new SftpDirectory(null, channel, session, uri, lstat);
					}
				}
			}
			catch (Exception e) {
				// ignore exception here, the file simply does not exist
			}
			
			// if we get here, no match was found, close the connection
			channel.exit();
			session.disconnect();
		}
		catch (Exception e) {
			// if we get here, close everything
			if (channel != null) {
				channel.exit();
			}
			if (session != null) {
				session.disconnect();
			}
			throw new IOException(e);
		}
		return null;
	}

	@Override
	public List<String> getDefaultSchemes() {
		return defaultSchemes;
	}

}
