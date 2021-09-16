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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.InflaterInputStream;

import core.BinaryReader;
import core.SDK;
import core.Texture;

/**
 * 热血传奇2WZL图片库
 * 
 * @author 云中双月
 */
public final class WZL implements ImageLibrary {

	private int imageCount;
	/**
	 * 获取库中图片数量
	 * 
	 * @return 存在于当前WZL库中的图片数量
	 */
	public int getImageCount() {
		return imageCount;
	}
    /* 图片数据起始位置 */
    private long[] offsetList;
    /* 图片数据长度 */
    private int[] lengthList;
    private ImageInfo[] imageInfos;
    /**
     * 获取库中图片信息数组
     * 
     * @return 所有存在于当前WZL库中的图片信息数组
     */
	public ImageInfo[] getImageInfos() {
		return imageInfos;
	}
	/* WZL文件随机读取对象 */
	private BinaryReader br_wzl;
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
    private Object wzl_locker = new Object();
    
    public WZL(String wzlPath) {
    	String wzxPath = SDK.changeFileExtension(wzlPath, "wzx");
		File f_wzx = new File(wzxPath);
		if(!f_wzx.exists()) return;
		if(!f_wzx.isFile()) return;
		if(!f_wzx.canRead()) return;
		File f_wzl = new File(wzlPath);
		if(!f_wzl.exists()) return;
		if(!f_wzl.isFile()) return;
		if(!f_wzl.canRead()) return;
    	try {
    		BinaryReader br_wzx = new BinaryReader(f_wzx);
    		br_wzx.skipBytes(44); // 跳过标题
    		imageCount = br_wzx.readIntLE();
			offsetList = new long[imageCount];
			for (int i = 0; i < imageCount; ++i)
			{
				// 读取数据偏移地址
				offsetList[i] = br_wzx.readUnsignedIntLE();
			}
			br_wzx.close();
			br_wzl = new BinaryReader(f_wzl);
			imageInfos = new ImageInfo[imageCount];
            lengthList = new int[imageCount];
            for (int i = 0; i < imageCount; ++i) {
            	long offset = offsetList[i];
            	if(offset <= 48) {
            		// WZL里offset小于64的是空图片
					imageInfos[i] = ImageInfo.EMPTY;
            		continue;
            	}
            	if(offset + 16 > br_wzl.length()) {
					// 数据出错，直接赋值为空图片
					imageInfos[i] = ImageInfo.EMPTY;
            		continue;
				}
                // 读取图片信息和数据长度
                ImageInfo ii = new ImageInfo();
                br_wzl.seek(offset);
                ii.setColorBit((byte) (br_wzl.readByte() == 5 ? 16 : 8));
                ii.wzlCompressed = br_wzl.readByte() != 0;
                br_wzl.skipBytes(2); // 跳过2字节未知数据
                ii.setWidth(br_wzl.readUnsignedShortLE());
				ii.setHeight(br_wzl.readUnsignedShortLE());
				ii.setOffsetX(br_wzl.readShortLE());
				ii.setOffsetY(br_wzl.readShortLE());
                imageInfos[i] = ii;
                lengthList[i] = (int) br_wzl.readUnsignedIntLE();
            }
            loaded = true;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
    }

    /** 从zlib解压 
     * @throws IOException */
	private static byte[] unzip(byte[] ziped) throws IOException {
		InflaterInputStream iis = new InflaterInputStream(new ByteArrayInputStream(ziped));
		ByteArrayOutputStream o = new ByteArrayOutputStream(1024);
		int i = 1024;
		byte[] buf = new byte[i];

		while ((i = iis.read(buf, 0, i)) > 0) {
			o.write(buf, 0, i);
		}
		return o.toByteArray();
	}
    
    /**
     * 关闭WZL对象，释放其引用的文件流以及内存占用
     */
	public final void close() throws IOException {
		synchronized (wzl_locker) {
			offsetList = null;
			lengthList = null;
            imageInfos = null;
            loaded = false;
			if (br_wzl != null)
            {
				br_wzl.close();
            }
		}
	}

	public final Texture tex(int index) {
		if(!loaded) return Texture.EMPTY;
		if(index < 0) return Texture.EMPTY;
		if(index >= imageCount) return Texture.EMPTY;
		if(imageInfos[index] == ImageInfo.EMPTY) return Texture.EMPTY;
		if(lengthList[index] == 0) return Texture.EMPTY;
    	try{
    		ImageInfo ii = imageInfos[index];
    		long offset = offsetList[index];
    		int length = lengthList[index];
    		byte[] pixels = new byte[length];
    		synchronized (wzl_locker) {
        		br_wzl.seek(offset + 16);
        		br_wzl.read(pixels);
			}
    		if(ii.wzlCompressed)
    			pixels = unzip(pixels);
    		byte[] sRGB = new byte[ii.getWidth() * ii.getHeight() * 3];
    		if (ii.getColorBit() == 8) {
                int p_index = 0;
                for (int h = ii.getHeight() - 1; h >= 0; --h)
                    for (int w = 0; w < ii.getWidth(); ++w) {
                        // 跳过填充字节
                        if (w == 0)
                            p_index += SDK.skipBytes(8, ii.getWidth());
                        byte[] pallete = SDK.palletes[pixels[p_index++] & 0xff];
    					int _idx = (w + h * ii.getWidth()) * 3;
    					sRGB[_idx] = pallete[1];
    					sRGB[_idx + 1] = pallete[2];
    					sRGB[_idx + 2] = pallete[3];
                    }
            }
	    	else if (ii.getColorBit() == 16) {
	    		ByteBuffer bb = ByteBuffer.wrap(pixels);
	    		bb.order(ByteOrder.LITTLE_ENDIAN);
	    		int p_index = 0;
                for (int h = ii.getHeight() - 1; h >= 0; --h)
                    for (int w = 0; w < ii.getWidth(); ++w, p_index += 2) {
                        // 跳过填充字节
                        if (w == 0)
                            p_index += SDK.skipBytes(16, ii.getWidth());
                        short pdata = bb.getShort(p_index);
                        byte r = (byte) ((pdata & 0xf800) >> 8);// 由于是与16位做与操作，所以多出了后面8位
                        byte g = (byte) ((pdata & 0x7e0) >> 3);// 多出了3位，在强转时前8位会自动丢失
                        byte b = (byte) ((pdata & 0x1f) << 3);// 少了3位
    					int _idx = (w + h * ii.getWidth()) * 3;
    					sRGB[_idx] = r;
    					sRGB[_idx + 1] = g;
    					sRGB[_idx + 2] = b;
                    }
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
