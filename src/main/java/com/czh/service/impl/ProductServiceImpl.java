package com.czh.service.impl;

import com.czh.dao.ProductDao;
import com.czh.dao.ProductImgDao;
import com.czh.dto.ImageHolder;
import com.czh.dto.ProductExecution;
import com.czh.entity.Product;
import com.czh.entity.ProductCategory;
import com.czh.entity.ProductImg;
import com.czh.enums.ProductStateEnum;
import com.czh.exceptions.ProductOperationException;
import com.czh.service.ProductService;
import com.czh.util.ImageUtil;
import com.czh.util.PageCalculator;
import com.czh.util.PathUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
@Service
public class ProductServiceImpl implements ProductService {
    @Autowired
    private ProductDao productDao;

    @Autowired
    private ProductImgDao productImgDao;

    @Override
    @Transactional
    //1 处理缩略图，获取缩略图相对路径并赋值给product
    //2 往tb_product写入商品信息，获取productId
    //3 结合productId批量处理商品详情图
    //4 将商品详情图列表批量插入tb_product_img中
    public ProductExecution addProduct(Product product, ImageHolder thumbnail, List<ImageHolder> productImgHolderList) throws ProductOperationException {
        //空值判断
        if (product != null && product.getShop() != null && product.getShop().getShopId() != null) {
            //给商品设置上默认属性
            product.setCreateTime(new Date());
            product.setLastEditTime(new Date());
            //默认为上架状态
            product.setEnableStatus(1);
            //若商品缩略图不为空则添加
            if (thumbnail != null) {
                addThumbnail(product,thumbnail);
            }
            try {
                //创建商品信息
                int effectedNum = productDao.insertProduct(product);
                if (effectedNum <= 0) {
                    throw new ProductOperationException("创建商品失败");
                }
            } catch (Exception e) {
                throw new ProductOperationException("创建商品失败：" + e.getMessage());
            }
            if (productImgHolderList != null && productImgHolderList.size() != 0) {
                addProductImgList(product, productImgHolderList);
            }
            return new ProductExecution(ProductStateEnum.SUCCESS);
        } else {
            return new ProductExecution(ProductStateEnum.EMPTY);
        }
    }

    @Override
    @Transactional
    //1若缩略图参数有值，则处理缩略图
    //2若原先存在缩略图则删除再添加新图，之后获取缩略图相对路径并赋值给product
    //3 若商品详情图列表参数有值，对商品详情图片列表进行同样的操作
    //4将tb_product_img下面的该商品原先的商品详情记录全部清除
    //5更新tb_product的信息
    public ProductExecution modifyProduct(Product product, ImageHolder thumbnail, List<ImageHolder> productImgHolderList) throws ProductOperationException {
        //控制判断
        if (product != null && product.getShop() !=null && product.getShop().getShopId() != null) {
            //给商品设置默认属性
            product.setLastEditTime(new Date());
            //若商品缩略图不为空，则删除原有缩略图变添加
            if (thumbnail != null) {
                //先获取一遍原有信息，因为原来的信息里有原图片地址
                Product tempProduct = productDao.queryByProductId(product.getProductId());
                if (tempProduct.getImgAddr() != null) {
                    ImageUtil.deleteFileOrPath(tempProduct.getImgAddr());
                }
                addThumbnail(product,thumbnail);
            }
            //如果有新存入的商品详情图片，则将原先的删除，并添加新的图片
            if (productImgHolderList != null && productImgHolderList.size() != 0) {
                deleteProductImgList(product.getProductId());
                addProductImgList(product, productImgHolderList);
            }
            try {
                //更新商品信息
                int effectedNum = productDao.updateProduct(product);
                if (effectedNum <= 0) {
                    throw new ProductOperationException("更新商品信息失败");
                } else {
                     return new ProductExecution(ProductStateEnum.SUCCESS);
                }
            } catch (Exception e) {
                throw new ProductOperationException("更新商品信息失败" + e.toString());
            }
        } else {
            return new ProductExecution(ProductStateEnum.EMPTY);
        }
    }

    @Override
    public Product getProductById(long productId) {
        return productDao.queryByProductId(productId);
    }

    @Override
    public ProductExecution getProductList(Product productCondition, int pageIndex, int pageSize) {
        int rowIndex = PageCalculator.calculateRowIndex(pageIndex, pageSize);
        List<Product> productList = productDao.queryProductList(productCondition, rowIndex, pageSize);
        int count = productDao.queryProductCount(productCondition);
        ProductExecution pe = new ProductExecution();
        pe.setProductList(productList);
        pe.setCount(count);
        return pe;
    }


    /*
    添加缩略图
    @param product
    @param thumbnail
     */
    private void addThumbnail(Product product, ImageHolder thumbnail) {
        String dest = PathUtil.getShopImagePath(product.getShop().getShopId());
        String thumbnailAddr = ImageUtil.generateThumbnail(thumbnail, dest);
        product.setImgAddr(thumbnailAddr);
    }

    /*
    添加批量图片
    @param product
    @param productImgHolderList
     */
    private void addProductImgList(Product product, List<ImageHolder> productImgHolderList) {
        //获取图片路径， 这里直接存放在相应店铺的文件夹底下
        String dest = PathUtil.getShopImagePath(product.getShop().getShopId());
        List<ProductImg> productImgList = new ArrayList<>();
        //遍历图片一次去处理， 并添加进productImg实体类里
        for (ImageHolder productImageHolder : productImgHolderList) {
            String imgAddr = ImageUtil.generateNormalImg(productImageHolder, dest);
            ProductImg productImg = new ProductImg();
            productImg.setImgAddr(imgAddr);
            productImg.setCreateTime(new Date());
            productImg.setProductId(product.getProductId());
            productImgList.add(productImg);
        }
        //如果确实有图片需要添加的，就执行批量添加操作
        if (productImgList.size() > 0) {
            try {
                int effectedNum = productImgDao.batchInsertProductImg(productImgList);
                if (effectedNum <= 0) {
                    throw new ProductOperationException("创建商品详情图片失败");
                }
            } catch (Exception e) {
                throw new ProductOperationException("创建商品详情图片失败" + e.getMessage());
            }
        }

    }

    public void deleteProductImgList(long productId) {
        //根据productId获取原来的图片
        List<ProductImg> productImgList = productImgDao.queryProductImgList(productId);
        //干掉原来的图片
        for (ProductImg productImg : productImgList) {
            ImageUtil.deleteFileOrPath(productImg.getImgAddr());
        }
        //删除数据库里原有图片的信息
        productImgDao.deleteProductImgByProductId(productId);
    }
}
