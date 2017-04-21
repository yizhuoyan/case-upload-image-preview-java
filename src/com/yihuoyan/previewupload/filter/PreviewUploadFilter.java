package com.yihuoyan.previewupload.filter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

import javax.imageio.ImageIO;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadBase.FileSizeLimitExceededException;
import org.apache.commons.fileupload.FileUploadBase.FileUploadIOException;
import org.apache.commons.fileupload.InvalidFileNameException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;

public class PreviewUploadFilter implements Filter {
	private int maxFileSize=50*1024;
	private int sizeThreshold = maxFileSize;
	private String allowType = "gif,png,jpeg,jpg,bmp";
	private String attributeName;

	@Override
	public void destroy() {

	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response,
			FilterChain chain) throws IOException, ServletException {
		HttpServletRequest req = (HttpServletRequest) request;
		HttpServletResponse resp = (HttpServletResponse) response;
		if ("get".equalsIgnoreCase(req.getMethod())) {
			String action = req.getParameter("__action");
			if ("preview".equals(action)) {
				preview(req, resp);
			} else {
				chain.doFilter(req, resp);
			}
		} else {
			Cookie[] cookies = req.getCookies();
			if (cookies != null) {
				for (Cookie cookie : cookies) {
					if ("action".equals(cookie.getName())) {
						if ("upload".equals(cookie.getValue())) {
							upload(req, resp);
							return;
						}
					}
				}
			}
			try {
				cut(req, resp);
			} catch (Exception e) {
				e.printStackTrace();
			}
			chain.doFilter(req, resp);
		}

	}

	public void upload(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		PrintWriter out = resp.getWriter();
		HttpSession session = req.getSession();
		String message = null;
		try {
			if (!ServletFileUpload.isMultipartContent(req)) {
				return;
			}
			DiskFileItemFactory itemFactory = new DiskFileItemFactory();
			itemFactory.setSizeThreshold(sizeThreshold);
			ServletFileUpload fileUpload = new ServletFileUpload(itemFactory);
			fileUpload.setFileSizeMax(maxFileSize);
			FileItemIterator fileItemIterator = fileUpload.getItemIterator(req);
			FileItemStream item = null;
			while (fileItemIterator.hasNext()) {
				item = fileItemIterator.next();
				if (!item.isFormField()) {
					String type = getFileType(item.getName());
					
					if (type == null || !allowType.contains(type.toLowerCase())) {
						throw new  InvalidFileNameException(type,"不支持文件类型,仅支持[" + allowType + "]");
					}
					String itemName = this.getClass().getName()
							+ item.getFieldName();
					InputStream is=item.openStream();
					byte[] bs=new byte[maxFileSize*8/10];
					ByteArrayOutputStream stream=new ByteArrayOutputStream(bs.length);
					int readed=0;
					while((readed=is.read(bs))!=-1){
						stream.write(bs, 0, readed);
						stream.flush();
					}
					session.setAttribute(itemName + "_data", stream);
				}
			}
		}catch (InvalidFileNameException e) {
			message=e.getMessage();
		}catch (FileUploadIOException e) {
			if(e.getCause().getClass()==FileSizeLimitExceededException.class){
				message="上传文件超过限制大小["+maxFileSize+"bytes]";
			}
		}catch (Throwable e) {
			message = "文件上传失败,请联系管理员";
			e.printStackTrace();
		} finally {
			if(message!=null){
				out.print(message);
			}
			out.close();
		}

	}

	private static final String getFileType(String path) {
		if (path == null || (path = path.trim()).length() == 0) {
			return null;
		}
		int begin = path.lastIndexOf('.');
		if (begin == -1) {
			return null;
		}
		return path.substring(begin+1);
	}

	public void preview(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		ServletOutputStream out = null;
		try {
			String itemName = req.getParameter("name");
			if (itemName == null) {
				return;
			}
			HttpSession httpSession = req.getSession();
			String sessionKeyName = this.getClass().getName() + itemName;
			out = resp.getOutputStream();
			ByteArrayOutputStream imageData = (ByteArrayOutputStream) httpSession.getAttribute(sessionKeyName
					+ "_data");
			if (imageData != null) {
				imageData.writeTo(out);
			}
		} finally {
			if (out != null)
				out.close();
		}

	}

	public void cut(HttpServletRequest req, HttpServletResponse resp)
			throws ServletException, IOException {
		Cookie[] cookies = req.getCookies();
		if (cookies == null)
			return;
		HttpSession httpSession = req.getSession();
		for (Cookie cookie : cookies) {
			if (cookie.getName().startsWith("cut")) {
				String cutName = cookie.getName().substring(3);
				String sessionKeyName = this.getClass().getName() + cutName;
				ByteArrayOutputStream imageData = (ByteArrayOutputStream) httpSession
						.getAttribute(sessionKeyName + "_data");
				if (imageData == null) {
					continue;
				}
				System.out.println(cookie.getValue());
				String[] posArray = cookie.getValue().split("!");
				int[] pos = new int[4];
				for (int i = 0; i < posArray.length; i++) {
					pos[i] = Integer.parseInt(posArray[i]);
				}
				BufferedImage bufferedImage = cutImage(imageData, pos[0],
						pos[1], pos[2] - pos[0], pos[3] - pos[1]);
				if(attributeName==null){
					attributeName=cutName;
				}
				req.setAttribute(attributeName, bufferedImage);
				imageData.close();
				httpSession.removeAttribute(sessionKeyName + "_data");
			}
		}

	}

	public static BufferedImage cutImage(ByteArrayOutputStream sourceData, int x, int y,
			int w, int h) throws IOException {

		BufferedImage bi = ImageIO.read(new ByteArrayInputStream(sourceData.toByteArray()));
		return bi.getSubimage(x, y, w, h);

	}

	@Override
	public void init(FilterConfig fc) throws ServletException {
		String allowType=fc.getInitParameter("allowType");
		if(allowType!=null){
			this.allowType=allowType.toLowerCase();
		}
		String maxFileSize=fc.getInitParameter("maxFileSize");
		if(maxFileSize!=null){
			this.maxFileSize=Integer.parseInt(maxFileSize);
		}
		String attributeName=fc.getInitParameter("attributeName");
		if(attributeName!=null){
			this.attributeName=attributeName;
		}
	}

}
