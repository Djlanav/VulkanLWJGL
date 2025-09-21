package dev.djlanav.main;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.*;
import org.lwjgl.stb.STBImage;

public class WindowManager {

	private static Logger logger = LogManager.getLogger();
	
	private static long window;
	private static int width;
	private static int height;
	
	private static ByteBuffer windowIcon = BufferUtils.createByteBuffer(1);
	private static GLFWImage windowIconImage = GLFWImage.calloc();
	private static GLFWImage.Buffer windowIconImageBuffer = GLFWImage.calloc(1);
	
	public static void initWindow(int w, int h, String title) {
		if (!GLFW.glfwInit()) {
			throw new RuntimeException("Failed to initialize GLFW!");
		} else {
			logger.info("Successfully initialized GLFW!");
		}
		
		GLFW.glfwWindowHint(GLFW.GLFW_CLIENT_API, GLFW.GLFW_NO_API);
		GLFW.glfwWindowHint(GLFW.GLFW_RESIZABLE, GLFW.GLFW_FALSE);
		
		width = w;
		height = h;
		
		window = GLFW.glfwCreateWindow(width, height, title, MemoryUtil.NULL, MemoryUtil.NULL);
		if (window == MemoryUtil.NULL) {
			throw new IllegalStateException("Window could not be created");
		}
		
		try (MemoryStack stack = MemoryStack.stackPush()) {
			IntBuffer widthBuffer = stack.callocInt(1);
			IntBuffer heightBuffer = stack.callocInt(1);
			IntBuffer channelsBuffer = stack.callocInt(1);
			
			widthBuffer.put(0, 32);
			heightBuffer.put(0, 32);
			channelsBuffer.put(0, 4);
			
			int iconW = widthBuffer.get(0);
			int iconH = heightBuffer.get(0);
			
			windowIcon = STBImage.stbi_load("res/lwjgl_32x32.jpg", widthBuffer, heightBuffer, channelsBuffer, 4);
			windowIconImage.set(iconW, iconH, windowIcon);
			windowIconImageBuffer.put(0, windowIconImage);
			GLFW.glfwSetWindowIcon(window, windowIconImageBuffer);
		}
		
		GLFWVidMode vidMode = GLFW.glfwGetVideoMode(GLFW.glfwGetPrimaryMonitor());
		GLFW.glfwSetWindowPos(window, (vidMode.width() - width) / 2, (vidMode.height() - height) / 2);
		GLFW.glfwMakeContextCurrent(window);
	}
	
	public static void updateWindowTitle(String newTitle) {
		GLFW.glfwSetWindowTitle(window, newTitle);
	}
	
	public static void cleanUp() {
		STBImage.stbi_image_free(windowIcon);
		windowIconImage.free();
		windowIconImageBuffer.free();
		
		GLFW.glfwDestroyWindow(window);
		GLFW.glfwTerminate();
	}
	
	public static long getWindow() {
		return window;
	}
	
	public static int getWindowHeight() {
		return height;
	}
	
	public static int getWindowWidth() {
		return width;
	}
}
