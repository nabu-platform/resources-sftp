package be.nabu.libs.resources.sftp;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Vector;

import be.nabu.libs.resources.URIUtils;
import be.nabu.libs.resources.api.ManageableContainer;
import be.nabu.libs.resources.api.Resource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.features.CacheableResource;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

public class SftpDirectory extends SftpResource implements ManageableContainer<SftpResource>, CacheableResource {

	protected SftpDirectory(ResourceContainer<?> parent, ChannelSftp channel, Session session, URI uri, SftpATTRS attrs) {
		super(parent, channel, session, uri, attrs);
	}

	private Map<String, SftpResource> children;
	private boolean cache;
	
	@Override
	public String getContentType() {
		return Resource.CONTENT_TYPE_DIRECTORY;
	}

	@Override
	public SftpResource getChild(String name) {
		return getChildren().get(name);
	}

	@Override
	public void resetCache() throws IOException {
		synchronized(this) {
			loadChildren();
		}
	}
	
	private Map<String, SftpResource> getChildren() {
		if (children == null || !cache) {
			synchronized(this) {
				if (children == null || !cache) {
					loadChildren();
				}
			}
		}
		return children;
	}
	
	@SuppressWarnings("unchecked")
	private void loadChildren() {
		Map<String, SftpResource> children = new HashMap<String, SftpResource>();
		try {
			Vector<LsEntry> list = getChannel().ls(getRemotePath());
			if (list != null) {
				for (LsEntry child : list) {
					if (child.getFilename().equals(".") || child.getFilename().equals("..")) {
						continue;
					}
					if (child.getAttrs().isDir()) {
						children.put(child.getFilename(), new SftpDirectory(this, getChannel(), getSession(), URIUtils.getChild(getUri(), child.getFilename()), child.getAttrs()));
					}
					else {
						children.put(child.getFilename(), new SftpItem(this, getChannel(), getSession(), URIUtils.getChild(getUri(), child.getFilename()), child.getAttrs()));
					}
				}
			}
			this.children = children;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public Iterator<SftpResource> iterator() {
		return getChildren().values().iterator();
	}

	@Override
	public void setCaching(boolean cache) {
		this.cache = cache;
	}

	@Override
	public boolean isCaching() {
		return cache;
	}

	@Override
	public SftpResource create(String name, String contentType) throws IOException {
		if (Resource.CONTENT_TYPE_DIRECTORY.equals(contentType)) {
			try {
				getChannel().mkdir(getRemotePath() + "/" + name);
			}
			catch (SftpException e) {
				throw new IOException(e);
			}
		}
		else {
			try {
				getChannel().put(new ByteArrayInputStream(new byte[0]), getRemotePath() + "/" + name);
			}
			catch (SftpException e) {
				throw new IOException(e);
			}
		}
		resetCache();
		return getChild(name);
	}

	@Override
	public void delete(String name) throws IOException {
		SftpResource child = getChild(name);
		try {
			if (child instanceof SftpItem) {
				getChannel().rm(child.getRemotePath());
			}
			else if (child instanceof SftpDirectory) {
				getChannel().rmdir(child.getRemotePath());
			}
		}
		catch (Exception e) {
			throw new IOException(e);
		}
	}
	
	protected void rename(String oldName, String newName) {
		Map<String, SftpResource> children = getChildren();
		children.put(newName, children.get(oldName));
		children.remove(oldName);
	}

}
