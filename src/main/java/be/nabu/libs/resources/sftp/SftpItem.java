package be.nabu.libs.resources.sftp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Date;

import be.nabu.libs.resources.api.AccessTrackingResource;
import be.nabu.libs.resources.api.AppendableResource;
import be.nabu.libs.resources.api.FiniteResource;
import be.nabu.libs.resources.api.ReadableResource;
import be.nabu.libs.resources.api.ResourceContainer;
import be.nabu.libs.resources.api.TimestampedResource;
import be.nabu.libs.resources.api.WritableResource;
import be.nabu.utils.io.IOUtils;
import be.nabu.utils.io.api.ByteBuffer;
import be.nabu.utils.io.api.ReadableContainer;
import be.nabu.utils.io.api.WritableContainer;

import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

public class SftpItem extends SftpResource implements AppendableResource, ReadableResource, WritableResource, FiniteResource, TimestampedResource, AccessTrackingResource {

	protected SftpItem(ResourceContainer<?> parent, ChannelSftp channel, Session session, URI uri, SftpATTRS attrs) {
		super(parent, channel, session, uri, attrs);
	}

	@Override
	public String getContentType() {
		return URLConnection.guessContentTypeFromName(getName());
	}

	@Override
	public Date getLastModified() {
		SftpATTRS attrs = getAttrs();
		if ((attrs.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
			// it is in seconds
			return new Date(1000l * attrs.getMTime());
		}
		return null;
	}

	@Override
	public long getSize() {
		SftpATTRS attrs = getAttrs();
		if ((attrs.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_SIZE) != 0) {
			return attrs.getSize();
		}
		return -1;
	}

	@Override
	public WritableContainer<ByteBuffer> getWritable() throws IOException {
		try {
			OutputStream out = getChannel().put(getRemotePath(), ChannelSftp.OVERWRITE);
			return IOUtils.wrap(out);
		}
		catch (SftpException e) {
			throw new IOException(e);
		}
	}

	@Override
	public WritableContainer<ByteBuffer> getAppendable() throws IOException {
		try {
			OutputStream out = getChannel().put(getRemotePath(), ChannelSftp.APPEND);
			return IOUtils.wrap(out);
		}
		catch (SftpException e) {
			throw new IOException(e);
		}
	}
	
	@Override
	public ReadableContainer<ByteBuffer> getReadable() throws IOException {
		try {
			InputStream input = getChannel().get(getRemotePath());
			return IOUtils.wrap(input);
		}
		catch (SftpException e) {
			throw new IOException(e);
		}
	}

	@Override
	public Date getLastAccessed() {
		SftpATTRS attrs = getAttrs();
		if ((attrs.getFlags() & SftpATTRS.SSH_FILEXFER_ATTR_ACMODTIME) != 0) {
			// it is in seconds
			return new Date(1000l * attrs.getATime());
		}
		return null;
	}

}
