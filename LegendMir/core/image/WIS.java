/*
 * Copyright 2017 JOOTNET Project
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 * Support: https://github.com/jootnet/mir2.core
 */
package core.image;

import java.io.File;
import java.io.IOException;
import core.BinaryReader;
import core.SDK;
import core.Texture;

/**
 * 热血传奇2WIS图片库
 * 
 * @author 云中双月
 */
public final class WIS implements ImageLibrary {

	private int imageCount;
	/**
	 * 获取库中图片数量
	 * 
	 * @return 存在于当前WIS库中的图片数量
	 */
	int getImageCount() {
		return imageCount;
	}
    /* 图片数据起始位置 */
    private int[] offsetList;
    /* 图片数据长度 */
    private int[] lengthList;
    private ImageInfo[] imageInfos;
    /**
     * 获取库中图片信息数组
     * 
     * @return 所有存在于当前WIS库中的图片信息数组
     */
	ImageInfo[] getImageInfos() {
		return imageInfos;
	}
	/* WIS文件随机读取对象 */
	private BinaryReader br_wis;
	private boolean loaded;
	/**
	 * 获取库加载状态
	 * 
	 * @return true表示库加载成功 false表示加载失败
	 */
	public boolean isLoaded() {
		return loaded;
	}
	/* 文件指针读取锁 */
    private Object wis_locker = new Object();
	
    public WIS(String wisPath) {
    	File f_wis = new File(wisPath);
		if(!f_wis.exists()) return;
		if(!f_wis.isFile()) return;
		if(!f_wis.canRead()) return;
    	try {
    		br_wis = new BinaryReader(f_wis);
			br_wis.seek(br_wis.length() - 12);
			int lastImageOff = br_wis.readIntLE();
			int lastImageLen = br_wis.readIntLE();
			imageCount = (int) ((br_wis.length() - lastImageOff - lastImageLen) / 12);
			imageInfos = new ImageInfo[imageCount];
			offsetList = new int[imageCount];
			lengthList = new int[imageCount];
			br_wis.seek(lastImageOff + lastImageLen);
			for(int i = 0; i < imageCount; ++i) {
				offsetList[i] = br_wis.readIntLE();
				lengthList[i] = br_wis.readIntLE();
				if(lengthList[i] == 13) {
					imageInfos[i] = ImageInfo.EMPTY;
				}
				br_wis.skipBytes(4);
			}
			for(int i = 0; i < imageCount; ++i) {
				if(imageInfos[i] != null) continue;
				ImageInfo ii = new ImageInfo();
				br_wis.seek(offsetList[i] + 4);
				ii.setWidth(br_wis.readShortLE());
				ii.setHeight(br_wis.readShortLE());
				ii.setOffsetX(br_wis.readShortLE());
				ii.setOffsetY(br_wis.readShortLE());
				imageInfos[i] = ii;
			}
			loaded = true;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
    }
        
    /**
	 * 解压数据
	 * @param packed 压缩的数据
	 * @param unpackLength 解压后数据大小
	 */
	private static byte[] unpack(byte[] packed, int unpackLength) {
		int srcLength = packed.length; // 压缩后数据大小
		byte[] result = new byte[unpackLength]; // 解压后数据
		int srcIndex = 0; // 当前解压的字节索引
		int dstIndex = 0; // 解压过程还原出的字节索引
		// 解压过程为逐字节进行(字节应转为1-256)
		// 如果当前字节非0则表示将以下一个字节数据填充当前字节个字节位置
		// 如果当前字节为0且下一个字节不为0则表示从下下个字节开始到下一个字节长度都没有压缩，直接复制到目标数组
		// 如果当前字节为0且下一个字节也为0则可能是脏数据，不予处理
		// XX YY 表示以YY填充XX个字节
		// 00 XX YY ZZ ... 表示从YY开始XX个字节是未被压缩的，直接复制出来即可
		while(srcLength > 0 && unpackLength > 0) {
			int length = packed[srcIndex++]; // 取出第一个标志位
			int value = packed[srcIndex++] & 0xff; // 取出第二个标志位
			srcLength -= 2;
			/*if(value == 0 && length == 0) {
				// 脏数据
				continue;
			} else */if(length != 0) {
				// 需要解压缩
				unpackLength -= length;
				for(int i = 0; i < length; ++i) {
					result[dstIndex++] = (byte) value;
				}
			} else if(value != 0) {
				srcLength -= value;
				unpackLength -= value;
				System.arraycopy(packed, srcIndex, result, dstIndex, value);
				dstIndex += value;
				srcIndex += value;
			}
		}
		return result;
	}
    
    /**
     * 关闭WIS对象，释放其引用的文件流以及内存占用
     */
	public final void close() throws IOException {
		synchronized (wis_locker) {
			offsetList = null;
			lengthList = null;
            imageInfos = null;
            loaded = false;
			if (br_wis != null)
            {
				br_wis.close();
            }
		}
	}

	public final Texture tex(int index) {
		if(!loaded) return Texture.EMPTY;
		if(index < 0) return Texture.EMPTY;
		if(index >= imageCount) return Texture.EMPTY;
		if(imageInfos[index] == ImageInfo.EMPTY) return Texture.EMPTY;
    	try{
    		ImageInfo ii = imageInfos[index];
    		int offset = offsetList[index];
    		int length = lengthList[index];
    		byte[] imageBytes = new byte[ii.getWidth() * ii.getHeight()];
    		byte[] packed = null;
    		byte encry = 0;
    		synchronized (wis_locker) {
        		// 是否压缩(RLE)
        		br_wis.seek(offset);
        		encry = br_wis.readByte();
        		br_wis.skipBytes(11);
        		if(encry == 1) {
        			// 压缩了
        			packed = new byte[length - 12];
        			br_wis.read(packed);
        		} else {
        			// 没压缩
        			br_wis.read(imageBytes);
        		}
			}
    		if(encry == 1)
    			imageBytes = unpack(packed, imageBytes.length);
    		byte[] sRGB = new byte[ii.getWidth() * ii.getHeight() * 3];
    		int index1 = 0;
    		for(int h = 0; h < ii.getHeight(); ++h)
    			for(int w = 0; w < ii.getWidth(); ++w) {
    				byte[] pallete = SDK.palletes[imageBytes[index1++] & 0xff];
					int _idx = (w + h * ii.getWidth()) * 3;
					sRGB[_idx] = pallete[1];
					sRGB[_idx + 1] = pallete[2];
					sRGB[_idx + 2] = pallete[3];
    			}
	    	return new Texture(sRGB, ii.getWidth(), ii.getHeight());
    	} catch(Exception ex) {
    		ex.printStackTrace();
    		return Texture.EMPTY;
    	}
    }

	public final ImageInfo info(int index) {
		if(!loaded) return ImageInfo.EMPTY;
		if(index < 0) return ImageInfo.EMPTY;
		if(index >= imageCount) return ImageInfo.EMPTY;
		return imageInfos[index];
	}

	public int count() {
		return imageCount;
	}

}
