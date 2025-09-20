package dev.djlanav.rendering;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;

public class ShaderManager {
	
	private Logger logger = LogManager.getLogger();
	
	private Shader mainVertex;
	private Shader mainFragment;
	
	public void loadCompiledShader(ShaderType shaderType) {
		String path;
		switch (shaderType) {
		case VERTEX:
			mainVertex = new Shader(ShaderType.VERTEX);
			path = new String("shaders/compiled/VertexShader.spv");
			
			ByteBuffer vertexBytes = readShaderBytes(path);
			mainVertex.setShaderBytes(vertexBytes);
			
			break;
		case FRAGMENT:
			mainFragment = new Shader(ShaderType.FRAGMENT);
			path = new String("shaders/compiled/FragmentShader.spv");
			
			ByteBuffer fragmentBytes = readShaderBytes(path);
			mainFragment.setShaderBytes(fragmentBytes);
			
			break;
		default:
			logger.error("Could not determine shader type");
			path = null;
			break;
		}
	}
	
	private ByteBuffer readShaderBytes(String path) {
		ByteBuffer shaderByteBuffer = null;
		File shaderFile = new File(path);
		Path shaderPath = shaderFile.toPath();
		
		try {
			byte[] shaderBytes = Files.readAllBytes(shaderPath);
			
			shaderByteBuffer = BufferUtils.createByteBuffer(shaderBytes.length);
			shaderByteBuffer.put(0, shaderBytes);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if (shaderByteBuffer != null) {
			return shaderByteBuffer;
		} else {
			throw new IllegalStateException("Shader byte buffer is null");
		}
	}

	public Shader getMainVertex() {
		return mainVertex;
	}

	public void setMainVertex(Shader mainVertex) {
		this.mainVertex = mainVertex;
	}

	public Shader getMainFragment() {
		return mainFragment;
	}

	public void setMainFragment(Shader mainFragment) {
		this.mainFragment = mainFragment;
	}
}
