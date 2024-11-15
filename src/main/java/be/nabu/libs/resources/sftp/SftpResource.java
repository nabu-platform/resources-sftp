/*
* Copyright (C) 2018 Alexander Verbruggen
*
* This program is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
* GNU Lesser General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with this program. If not, see <https://www.gnu.org/licenses/>.
*/

package be.nabu.libs.resources.sftp;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;

import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.LocatableResource;
import be.nabu.libs.resources.api.RenameableResource;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;

abstract public class SftpResource implements Resource, Closeable, LocatableResource, RenameableResource {

	private ChannelSftp channel;
	private URI uri;
	private ResourceContainer<?> parent;
	private SftpATTRS attrs;
	private Session session;
	protected boolean absolute;

	protected SftpResource(ResourceContainer<?> parent, ChannelSftp channel, Session session, URI uri, SftpATTRS attrs) {
		this.parent = parent;
		this.channel = channel;
		this.session = session;
		this.attrs = attrs;
		this.uri = uri;
		this.absolute = parent == null ? isAbsolute(uri) : ((SftpDirectory) parent).absolute; 
	}

	public static boolean isAbsolute(URI uri) {
		if (uri.getPath().startsWith("//")) {
			return true;
		}
		else {
			Map<String, List<String>> queryProperties = URIUtils.getQueryProperties(uri);
			if (queryProperties.get("absolute") != null && queryProperties.get("absolute").size() > 0 && "true".equals(queryProperties.get("absolute").get(0))) {
				return true;
			}
		}
		return false;
	}
	
	@Override
	public URI getUri() {
		return uri;
	}

	@Override
	public void close() throws IOException {
		channel.exit();
		session.disconnect();
	}

	@Override
	public String getName() {
		String path = getRemotePath();
		int index = path.lastIndexOf('/');
		return index < 0 ? path : path.substring(index + 1);
	}

	protected String getRemotePath() {
		String path = this.uri.getPath();
		// skip leading slash
		if (!absolute) {
			path = path.substring(1);
		}
		return path.isEmpty() && this instanceof ResourceContainer ? "." : path;
	}
	
	@Override
	public ResourceContainer<?> getParent() {
		return parent;
	}
	
	protected SftpATTRS getAttrs() {
		return attrs;
	}
	
	protected ChannelSftp getChannel() {
		return channel;
	}
	
	protected Session getSession() {
		return session;
	}

	@Override
	public void rename(String name) throws IOException {
		URI newUri = URIUtils.getChild(URIUtils.getParent(getUri()), name);
		SftpDirectory parent = (SftpDirectory) getParent();
		String newPath = newUri.getPath();
		if (!absolute) {
			newPath = newPath.substring(1);
		}
		String oldName = getName();
		try {
			channel.rename(getRemotePath(), newPath);
		}
		catch (Exception e) {
			throw new IOException(e);
		}
		uri = newUri;
		if (parent != null) {
			parent.rename(oldName, name);
		}
	}
	
}
