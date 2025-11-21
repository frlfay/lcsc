<template>
  <div class="category-management">
    <!-- 标签页 -->
    <a-tabs v-model:activeKey="activeTab" @change="handleTabClick">
      <a-tab-pane key="level1" tab="一级分类管理">
        <!-- 一级分类搜索区域 -->
        <a-card class="mb-4">
          <a-form :model="level1SearchForm" layout="inline">
            <a-form-item label="分类名称">
              <a-input 
                v-model:value="level1SearchForm.categoryName" 
                placeholder="输入分类名称"
                allow-clear
                style="width: 200px"
              />
            </a-form-item>
            <a-form-item label="分类码">
              <a-input 
                v-model:value="level1SearchForm.categoryCode" 
                placeholder="输入分类码"
                allow-clear
                style="width: 200px"
              />
            </a-form-item>
          </a-form>
          <div class="search-actions">
            <a-button type="primary" @click="handleLevel1Search">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
            <a-button @click="handleLevel1Reset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
            <a-button type="primary" @click="showLevel1AddDialog = true">
              <template #icon><PlusOutlined /></template>
              新增一级分类
            </a-button>
          </div>
        </a-card>

        <!-- 一级分类列表 -->
        <a-card>
          <a-table 
            :dataSource="level1TableData" 
            :loading="level1Loading"
            :pagination="{
              current: level1Pagination.current,
              pageSize: level1Pagination.size,
              total: level1Pagination.total,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total: number) => `共 ${total} 条`,
              pageSizeOptions: ['10', '20', '50', '100'],
              onChange: handleLevel1CurrentChange,
              onShowSizeChange: handleLevel1SizeChange
            }"
            bordered
            row-key="id"
          >
            <a-table-column title="ID" dataIndex="id" width="80" />
            <a-table-column title="分类名称" dataIndex="categoryLevel1Name" width="200">
              <template #default="{ record }">
                <div>
                  {{ record.categoryLevel1Name }}
                  <a-tag v-if="record.isCustomized === 1" color="blue" style="margin-left: 8px; font-size: 11px">
                    自定义
                  </a-tag>
                </div>
                <div v-if="record.isCustomized === 1 && record.sourceName" style="font-size: 12px; color: #999; margin-top: 4px;">
                  API源名: {{ record.sourceName }}
                </div>
              </template>
            </a-table-column>
            <a-table-column title="分类码" dataIndex="categoryCode" width="150" />
            <a-table-column title="创建时间" dataIndex="createdAt" width="180">
              <template #default="{ record }">
                {{ formatDateTime(record.createdAt) }}
              </template>
            </a-table-column>
            <a-table-column title="更新时间" dataIndex="updatedAt" width="180">
              <template #default="{ record }">
                {{ formatDateTime(record.updatedAt) }}
              </template>
            </a-table-column>
            <a-table-column title="操作" width="200" fixed="right">
              <template #default="{ record }">
                <a-button size="small" @click="handleEditCategoryName(record, 'level1')" style="margin-right: 8px;">
                  <template #icon><EditOutlined /></template>
                  编辑名称
                </a-button>
                <a-button size="small" @click="handleLevel1Edit(record)">
                  <template #icon><EditOutlined /></template>
                  编辑
                </a-button>
                <a-button size="small" @click="viewLevel2Categories(record)" style="margin-left: 8px;">
                  <template #icon><UnorderedListOutlined /></template>
                  二级分类
                </a-button>
                <a-button size="small" danger @click="handleLevel1Delete(record)" style="margin-left: 8px;">
                  <template #icon><DeleteOutlined /></template>
                  删除
                </a-button>
              </template>
            </a-table-column>
          </a-table>
        </a-card>
      </a-tab-pane>

<!--      <a-tab-pane key="patch" tab="分类补丁">-->
<!--        &lt;!&ndash; 分类补丁功能 &ndash;&gt;-->
<!--        <a-card class="mb-4">-->
<!--          <div class="patch-section">-->
<!--            <h3>分类补丁操作</h3>-->
<!--            <p class="text-muted">用于补充缺失的分类数据，解决产品无法按分类筛选的问题</p>-->
<!--            -->
<!--            &lt;!&ndash; 操作按钮组 &ndash;&gt;-->
<!--            <div class="patch-actions">-->
<!--              <a-space wrap>-->
<!--                <a-button -->
<!--                  type="primary" -->
<!--                  size="large"-->
<!--                  :loading="patchLoading.fromProducts"-->
<!--                  @click="handlePatchFromProducts"-->
<!--                >-->
<!--                  <template #icon><DatabaseOutlined /></template>-->
<!--                  一键补丁（推荐）-->
<!--                </a-button>-->
<!--                -->
<!--                <a-button -->
<!--                  type="default"-->
<!--                  size="large" -->
<!--                  :loading="patchLoading.crawlAll"-->
<!--                  @click="handleCrawlAll"-->
<!--                >-->
<!--                  <template #icon><GlobalOutlined /></template>-->
<!--                  爬取官方分类-->
<!--                </a-button>-->
<!--                -->
<!--                <a-button -->
<!--                  size="large"-->
<!--                  @click="showBatchPatchDialog = true"-->
<!--                >-->
<!--                  <template #icon><EditOutlined /></template>-->
<!--                  手动批量处理-->
<!--                </a-button>-->
<!--                -->
<!--                <a-button -->
<!--                  size="large"-->
<!--                  @click="showSingleAddDialog = true"-->
<!--                >-->
<!--                  <template #icon><PlusOutlined /></template>-->
<!--                  添加单个分类-->
<!--                </a-button>-->
<!--              </a-space>-->
<!--            </div>-->
<!--            -->
<!--            &lt;!&ndash; 功能说明 &ndash;&gt;-->
<!--            <div class="patch-help">-->
<!--              <a-alert-->
<!--                message="功能说明"-->
<!--                type="info"-->
<!--                show-icon-->
<!--              >-->
<!--                <template #description>-->
<!--                  <ul>-->
<!--                    <li><strong>一键补丁（推荐）</strong>：从现有产品数据中智能提取分类信息并自动入库</li>-->
<!--                    <li><strong>爬取官方分类</strong>：从立创商城API获取完整的官方分类结构</li>-->
<!--                    <li><strong>手动批量处理</strong>：手动指定要处理的分类组合列表</li>-->
<!--                    <li><strong>添加单个分类</strong>：手动添加单个分类组合</li>-->
<!--                  </ul>-->
<!--                </template>-->
<!--              </a-alert>-->
<!--            </div>-->
<!--          </div>-->
<!--        </a-card>-->
<!--        -->
<!--        &lt;!&ndash; 统计信息 &ndash;&gt;-->
<!--        <a-card class="mb-4">-->
<!--          <div class="patch-stats">-->
<!--            <h4>处理统计</h4>-->
<!--            <a-row :gutter="16">-->
<!--              <a-col :span="6">-->
<!--                <a-statistic title="缓存中一级分类" :value="cacheStats.level1CacheSize" />-->
<!--              </a-col>-->
<!--              <a-col :span="6">-->
<!--                <a-statistic title="缓存中二级分类" :value="cacheStats.level2CacheSize" />-->
<!--              </a-col>-->
<!--              <a-col :span="6">-->
<!--                <a-button @click="refreshStats">-->
<!--                  <template #icon><ReloadOutlined /></template>-->
<!--                  刷新统计-->
<!--                </a-button>-->
<!--              </a-col>-->
<!--              <a-col :span="6">-->
<!--                <a-button @click="handleClearCache">-->
<!--                  <template #icon><DeleteOutlined /></template>-->
<!--                  清空缓存-->
<!--                </a-button>-->
<!--              </a-col>-->
<!--            </a-row>-->
<!--          </div>-->
<!--        </a-card>-->
<!--        -->
<!--        &lt;!&ndash; 最新处理结果 &ndash;&gt;-->
<!--        <a-card v-if="lastResult" class="mb-4">-->
<!--          <div class="patch-result">-->
<!--            <h4>最新处理结果</h4>-->
<!--            <a-descriptions :column="3" bordered>-->
<!--              <a-descriptions-item label="处理状态">-->
<!--                <a-tag :color="lastResult.success ? 'green' : 'red'">-->
<!--                  {{ lastResult.success ? '成功' : '失败' }}-->
<!--                </a-tag>-->
<!--              </a-descriptions-item>-->
<!--              <a-descriptions-item label="耗时">{{ lastResult.durationMs }}ms</a-descriptions-item>-->
<!--              <a-descriptions-item label="一级分类总数">{{ lastResult.totalLevel1Categories }}</a-descriptions-item>-->
<!--              <a-descriptions-item label="二级分类总数">{{ lastResult.totalLevel2Categories }}</a-descriptions-item>-->
<!--              <a-descriptions-item label="新建一级分类">{{ lastResult.createdLevel1 }}</a-descriptions-item>-->
<!--              <a-descriptions-item label="新建二级分类">{{ lastResult.createdLevel2 }}</a-descriptions-item>-->
<!--              <a-descriptions-item label="处理总数">{{ lastResult.processed }}</a-descriptions-item>-->
<!--              <a-descriptions-item label="失败数">{{ lastResult.failed }}</a-descriptions-item>-->
<!--              <a-descriptions-item label="跳过数">{{ lastResult.skipped }}</a-descriptions-item>-->
<!--            </a-descriptions>-->
<!--            <div v-if="!lastResult.success && lastResult.errorMessage" class="mt-2">-->
<!--              <a-alert :message="lastResult.errorMessage" type="error" />-->
<!--            </div>-->
<!--          </div>-->
<!--        </a-card>-->
<!--      </a-tab-pane>-->

      <a-tab-pane key="level2" tab="二级分类管理">
        <!-- 二级分类搜索区域 -->
        <a-card class="mb-4">
          <a-form :model="level2SearchForm" layout="inline">
            <a-form-item label="分类名称">
              <a-input
                v-model:value="level2SearchForm.categoryName"
                placeholder="输入分类名称"
                allow-clear
                style="width: 200px"
              />
            </a-form-item>
            <a-form-item label="一级分类">
              <a-select
                v-model:value="level2SearchForm.categoryLevel1Id"
                placeholder="选择一级分类"
                allow-clear
                style="width: 200px"
              >
                <a-select-option
                  v-for="item in allLevel1Categories"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.categoryLevel1Name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="爬取状态">
              <a-select
                v-model:value="level2SearchForm.crawlStatus"
                placeholder="选择爬取状态"
                allow-clear
                style="width: 150px"
              >
                <a-select-option value="pending">待爬取</a-select-option>
                <a-select-option value="processing">爬取中</a-select-option>
                <a-select-option value="completed">已完成</a-select-option>
                <a-select-option value="failed">失败</a-select-option>
              </a-select>
            </a-form-item>
          </a-form>
          <div class="search-actions">
            <a-button type="primary" @click="handleLevel2Search">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
            <a-button @click="handleLevel2Reset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
            <a-button type="primary" @click="showLevel2AddDialog = true">
              <template #icon><PlusOutlined /></template>
              新增二级分类
            </a-button>
          </div>
        </a-card>

        <!-- 二级分类列表 -->
        <a-card>
          <a-table
            :dataSource="level2TableData"
            :loading="level2Loading"
            :pagination="{
              current: level2Pagination.current,
              pageSize: level2Pagination.size,
              total: level2Pagination.total,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total: number) => `共 ${total} 条`,
              pageSizeOptions: ['10', '20', '50', '100'],
              onChange: handleLevel2CurrentChange,
              onShowSizeChange: handleLevel2SizeChange
            }"
            bordered
            row-key="id"
            :scroll="{ x: 1500 }"
          >
            <a-table-column title="ID" dataIndex="id" width="80" fixed="left" />
            <a-table-column title="分类名称" dataIndex="categoryLevel2Name" width="200" fixed="left">
              <template #default="{ record }">
                <div>
                  {{ record.categoryLevel2Name }}
                  <a-tag v-if="record.isCustomized === 1" color="blue" style="margin-left: 8px; font-size: 11px">
                    自定义
                  </a-tag>
                </div>
                <div v-if="record.isCustomized === 1 && record.sourceName" style="font-size: 12px; color: #999; margin-top: 4px;">
                  API源名: {{ record.sourceName }}
                </div>
              </template>
            </a-table-column>
            <a-table-column title="一级分类" dataIndex="categoryLevel1Id" width="150">
              <template #default="{ record }">
                {{ getLevel1Name(record.categoryLevel1Id) }}
              </template>
            </a-table-column>
            <a-table-column title="爬取状态" dataIndex="crawlStatus" width="120">
              <template #default="{ record }">
                <a-badge
                  :status="getCrawlStatusBadge(record.crawlStatus)"
                  :text="getCrawlStatusText(record.crawlStatus)"
                />
              </template>
            </a-table-column>
            <a-table-column title="爬取进度" width="180">
              <template #default="{ record }">
                <div v-if="record.crawlProgress !== null && record.crawlProgress !== undefined">
                  <a-progress
                    :percent="record.crawlProgress"
                    :status="getProgressStatus(record.crawlStatus)"
                    size="small"
                  />
                </div>
                <span v-else>-</span>
              </template>
            </a-table-column>
            <a-table-column title="产品数量" width="150">
              <template #default="{ record }">
                <span v-if="record.totalProducts">
                  {{ record.crawledProducts || 0 }} / {{ record.totalProducts }}
                </span>
                <span v-else>-</span>
              </template>
            </a-table-column>
            <a-table-column title="最后爬取时间" dataIndex="lastCrawlTime" width="180">
              <template #default="{ record }">
                {{ formatDateTime(record.lastCrawlTime) }}
              </template>
            </a-table-column>
            <a-table-column title="店铺分类码" dataIndex="shopCategoryCodes" width="120">
              <template #default="{ record }">
                <a-tag v-if="record.shopCategoryCodes" color="blue">已配置</a-tag>
                <span v-else>-</span>
              </template>
            </a-table-column>
            <a-table-column title="操作" width="380" fixed="right">
              <template #default="{ record }">
                <a-space>
                  <a-button size="small" @click="handleEditCategoryName(record, 'level2')">
                    <template #icon><EditOutlined /></template>
                    编辑名称
                  </a-button>
                  <a-button
                    size="small"
                    type="primary"
                    @click="startCategoryCrawl(record)"
                    :loading="record._crawling"
                  >
                    <template #icon><ThunderboltOutlined /></template>
                    爬取
                  </a-button>
                  <a-button
                    size="small"
                    @click="viewCrawlProgress(record)"
                    :disabled="!record.crawlStatus || record.crawlStatus === 'pending'"
                  >
                    <template #icon><LineChartOutlined /></template>
                    进度
                  </a-button>
                  <a-button size="small" @click="handleLevel2Edit(record)">
                    <template #icon><EditOutlined /></template>
                    编辑
                  </a-button>
                  <a-button size="small" @click="manageShopCodes(record)">
                    <template #icon><SettingOutlined /></template>
                    店铺码
                  </a-button>
                  <a-button size="small" danger @click="handleLevel2Delete(record)">
                    <template #icon><DeleteOutlined /></template>
                    删除
                  </a-button>
                </a-space>
              </template>
            </a-table-column>
          </a-table>
        </a-card>
      </a-tab-pane>

      <a-tab-pane key="level3" tab="三级分类管理">
        <!-- 三级分类搜索区域 -->
        <a-card class="mb-4">
          <a-form :model="level3SearchForm" layout="inline">
            <a-form-item label="分类名称">
              <a-input
                v-model:value="level3SearchForm.categoryName"
                placeholder="输入分类名称"
                allow-clear
                style="width: 200px"
              />
            </a-form-item>
            <a-form-item label="一级分类">
              <a-select
                v-model:value="level3SearchForm.categoryLevel1Id"
                placeholder="选择一级分类"
                allow-clear
                style="width: 200px"
                @change="handleLevel3Level1Change"
              >
                <a-select-option
                  v-for="item in allLevel1Categories"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.categoryLevel1Name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="二级分类">
              <a-select
                v-model:value="level3SearchForm.categoryLevel2Id"
                placeholder="选择二级分类"
                allow-clear
                style="width: 200px"
                :disabled="!level3SearchForm.categoryLevel1Id"
              >
                <a-select-option
                  v-for="item in level3Level2Categories"
                  :key="item.id"
                  :value="item.id"
                >
                  {{ item.categoryLevel2Name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="爬取状态">
              <a-select
                v-model:value="level3SearchForm.crawlStatus"
                placeholder="选择爬取状态"
                allow-clear
                style="width: 150px"
              >
                <a-select-option value="pending">待爬取</a-select-option>
                <a-select-option value="processing">爬取中</a-select-option>
                <a-select-option value="completed">已完成</a-select-option>
                <a-select-option value="failed">失败</a-select-option>
              </a-select>
            </a-form-item>
          </a-form>
          <div class="search-actions">
            <a-button type="primary" @click="handleLevel3Search">
              <template #icon><SearchOutlined /></template>
              搜索
            </a-button>
            <a-button @click="handleLevel3Reset">
              <template #icon><ReloadOutlined /></template>
              重置
            </a-button>
            <a-button type="primary" @click="showLevel3AddDialog = true">
              <template #icon><PlusOutlined /></template>
              新增三级分类
            </a-button>
          </div>
        </a-card>

        <!-- 三级分类列表 -->
        <a-card>
          <a-table
            :dataSource="level3TableData"
            :loading="level3Loading"
            :pagination="{
              current: level3Pagination.current,
              pageSize: level3Pagination.size,
              total: level3Pagination.total,
              showSizeChanger: true,
              showQuickJumper: true,
              showTotal: (total: number) => `共 ${total} 条`,
              pageSizeOptions: ['10', '20', '50', '100'],
              onChange: handleLevel3CurrentChange,
              onShowSizeChange: handleLevel3SizeChange
            }"
            bordered
            row-key="id"
            :scroll="{ x: 1800 }"
          >
            <a-table-column title="ID" dataIndex="id" width="80" fixed="left" />
            <a-table-column title="分类名称" dataIndex="categoryLevel3Name" width="200" fixed="left">
              <template #default="{ record }">
                <div>
                  {{ record.categoryLevel3Name }}
                  <a-tag v-if="record.isCustomized === 1" color="blue" style="margin-left: 8px; font-size: 11px">
                    自定义
                  </a-tag>
                </div>
                <div v-if="record.isCustomized === 1 && record.sourceName" style="font-size: 12px; color: #999; margin-top: 4px;">
                  API源名: {{ record.sourceName }}
                </div>
              </template>
            </a-table-column>
            <a-table-column title="一级分类" dataIndex="categoryLevel1Id" width="150">
              <template #default="{ record }">
                {{ getLevel1Name(record.categoryLevel1Id) }}
              </template>
            </a-table-column>
            <a-table-column title="二级分类" dataIndex="categoryLevel2Id" width="150">
              <template #default="{ record }">
                {{ getLevel2Name(record.categoryLevel2Id) }}
              </template>
            </a-table-column>
            <a-table-column title="目录ID" dataIndex="catalogId" width="120" />
            <a-table-column title="爬取状态" dataIndex="crawlStatus" width="120">
              <template #default="{ record }">
                <a-badge
                  :status="getCrawlStatusBadge(record.crawlStatus)"
                  :text="getCrawlStatusText(record.crawlStatus)"
                />
              </template>
            </a-table-column>
            <a-table-column title="爬取进度" width="180">
              <template #default="{ record }">
                <div v-if="record.crawlProgress !== null && record.crawlProgress !== undefined">
                  <a-progress
                    :percent="record.crawlProgress"
                    :status="getProgressStatus(record.crawlStatus)"
                    size="small"
                  />
                </div>
                <span v-else>-</span>
              </template>
            </a-table-column>
            <a-table-column title="产品数量" width="150">
              <template #default="{ record }">
                <span v-if="record.totalProducts">
                  {{ record.crawledProducts || 0 }} / {{ record.totalProducts }}
                </span>
                <span v-else>-</span>
              </template>
            </a-table-column>
            <a-table-column title="最后爬取时间" dataIndex="lastCrawlTime" width="180">
              <template #default="{ record }">
                {{ formatDateTime(record.lastCrawlTime) }}
              </template>
            </a-table-column>
            <a-table-column title="操作" width="240" fixed="right">
              <template #default="{ record }">
                <a-space>
                  <a-button size="small" @click="handleEditCategoryName(record, 'level3')">
                    <template #icon><EditOutlined /></template>
                    编辑名称
                  </a-button>
                  <a-button size="small" @click="handleLevel3Edit(record)">
                    <template #icon><EditOutlined /></template>
                    编辑
                  </a-button>
                  <a-button size="small" danger @click="handleLevel3Delete(record)">
                    <template #icon><DeleteOutlined /></template>
                    删除
                  </a-button>
                </a-space>
              </template>
            </a-table-column>
          </a-table>
        </a-card>
      </a-tab-pane>
    </a-tabs>

    <!-- 新增/编辑一级分类对话框 -->
    <a-modal v-model:open="showLevel1AddDialog" :title="editingLevel1.id ? '编辑一级分类' : '新增一级分类'" width="600px" @ok="handleSaveLevel1">
      <a-form
        ref="level1FormRef"
        :model="editingLevel1"
        :rules="level1Rules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="分类名称" name="categoryLevel1Name">
          <a-input v-model:value="editingLevel1.categoryLevel1Name" placeholder="输入分类名称" />
        </a-form-item>
        <a-form-item label="分类码" name="categoryCode">
          <a-input v-model:value="editingLevel1.categoryCode" placeholder="输入分类码（可选）" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增/编辑二级分类对话框 -->
    <a-modal v-model:open="showLevel2AddDialog" :title="editingLevel2.id ? '编辑二级分类' : '新增二级分类'" width="600px" @ok="handleSaveLevel2">
      <a-form
        ref="level2FormRef"
        :model="editingLevel2"
        :rules="level2Rules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="分类名称" name="categoryLevel2Name">
          <a-input v-model:value="editingLevel2.categoryLevel2Name" placeholder="输入分类名称" />
        </a-form-item>
        <a-form-item label="所属一级分类" name="categoryLevel1Id">
          <a-select
            v-model:value="editingLevel2.categoryLevel1Id"
            placeholder="选择一级分类"
            style="width: 100%"
          >
            <a-select-option
              v-for="item in allLevel1Categories"
              :key="item.id"
              :value="item.id"
            >
              {{ item.categoryLevel1Name }}
            </a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 新增/编辑三级分类对话框 -->
    <a-modal v-model:open="showLevel3AddDialog" :title="editingLevel3.id ? '编辑三级分类' : '新增三级分类'" width="600px" @ok="handleSaveLevel3">
      <a-form
        ref="level3FormRef"
        :model="editingLevel3"
        :rules="level3Rules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="分类名称" name="categoryLevel3Name">
          <a-input v-model:value="editingLevel3.categoryLevel3Name" placeholder="输入分类名称" />
        </a-form-item>
        <a-form-item label="所属一级分类" name="categoryLevel1Id">
          <a-select
            v-model:value="editingLevel3.categoryLevel1Id"
            placeholder="选择一级分类"
            style="width: 100%"
            @change="handleLevel3EditLevel1Change"
          >
            <a-select-option
              v-for="item in allLevel1Categories"
              :key="item.id"
              :value="item.id"
            >
              {{ item.categoryLevel1Name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="所属二级分类" name="categoryLevel2Id">
          <a-select
            v-model:value="editingLevel3.categoryLevel2Id"
            placeholder="选择二级分类"
            style="width: 100%"
            :disabled="!editingLevel3.categoryLevel1Id"
          >
            <a-select-option
              v-for="item in level3EditLevel2Categories"
              :key="item.id"
              :value="item.id"
            >
              {{ item.categoryLevel2Name }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="目录ID" name="catalogId">
          <a-input v-model:value="editingLevel3.catalogId" placeholder="输入目录ID" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 店铺分类码管理对话框 -->
    <a-modal v-model:open="showShopCodeDialog" title="店铺分类码管理" width="800px" :footer="null">
      <div v-if="selectedLevel2Category">
        <h4>{{ selectedLevel2Category.categoryLevel2Name }} 的店铺分类码</h4>
        <a-table :dataSource="shopCodeList" bordered row-key="id">
          <a-table-column title="店铺名称" dataIndex="shopName" width="200" />
          <a-table-column title="运费模板ID" dataIndex="shippingTemplateId" width="150" />
          <a-table-column title="分类码" width="200">
            <template #default="{ record }">
              <a-input
                v-model:value="record.categoryCode"
                placeholder="输入分类码"
                size="small"
              />
            </template>
          </a-table-column>
          <a-table-column title="操作" width="120">
            <template #default="{ record }">
              <a-button size="small" type="primary" @click="saveShopCode(record)">
                保存
              </a-button>
            </template>
          </a-table-column>
        </a-table>
      </div>
    </a-modal>

    <!-- 手动批量处理对话框 -->
    <a-modal 
      v-model:open="showBatchPatchDialog" 
      title="手动批量处理分类" 
      width="800px" 
      @ok="handleBatchPatch"
      :ok-button-props="{ loading: batchPatchLoading }"
    >
      <div class="batch-patch-form">
        <p>请输入要处理的分类组合，每行一个，格式：一级分类名称,二级分类名称</p>
        <a-textarea 
          v-model:value="batchPatchText" 
          :rows="10" 
          placeholder="例如：&#10;电阻,贴片电阻&#10;电容,陶瓷电容&#10;集成电路,运算放大器"
        />
        <div class="mt-2">
          <a-space>
            <a-button @click="loadSampleData">加载示例数据</a-button>
            <a-button @click="clearBatchPatchText">清空</a-button>
          </a-space>
        </div>
      </div>
    </a-modal>

    <!-- 添加单个分类对话框 -->
    <a-modal
      v-model:open="showSingleAddDialog"
      title="添加单个分类"
      width="600px"
      @ok="handleSingleAdd"
      :ok-button-props="{ loading: singleAddLoading }"
    >
      <a-form
        ref="singleAddFormRef"
        :model="singleAddForm"
        :rules="singleAddRules"
        :label-col="{ span: 6 }"
        :wrapper-col="{ span: 18 }"
      >
        <a-form-item label="一级分类名称" name="level1Name">
          <a-input v-model:value="singleAddForm.level1Name" placeholder="输入一级分类名称" />
        </a-form-item>
        <a-form-item label="二级分类名称" name="level2Name">
          <a-input v-model:value="singleAddForm.level2Name" placeholder="输入二级分类名称" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 爬取进度对话框 -->
    <a-modal
      v-model:open="showProgressDialog"
      :title="`分类爬取进度 - ${progressCategory?.categoryLevel2Name || ''}`"
      width="800px"
      :footer="null"
      @cancel="closeProgressDialog"
    >
      <div v-if="progressCategory" class="progress-dialog">
        <!-- 基本信息 -->
        <a-descriptions bordered :column="2" class="mb-4">
          <a-descriptions-item label="分类ID">
            {{ progressCategory.id }}
          </a-descriptions-item>
          <a-descriptions-item label="分类名称">
            {{ progressCategory.categoryLevel2Name }}
          </a-descriptions-item>
          <a-descriptions-item label="一级分类">
            {{ getLevel1Name(progressCategory.categoryLevel1Id) }}
          </a-descriptions-item>
          <a-descriptions-item label="爬取状态">
            <a-badge
              :status="getCrawlStatusBadge(progressCategory.crawlStatus)"
              :text="getCrawlStatusText(progressCategory.crawlStatus)"
            />
          </a-descriptions-item>
        </a-descriptions>

        <!-- 实时进度 -->
        <a-card title="实时进度" class="mb-4">
          <div class="progress-details">
            <div class="progress-item">
              <h4>总体进度</h4>
              <a-progress
                :percent="progressData.progress || progressCategory.crawlProgress || 0"
                :status="getProgressStatus(progressCategory.crawlStatus)"
              />
            </div>
            <a-row :gutter="16" class="mt-4">
              <a-col :span="8">
                <a-statistic
                  title="当前页码"
                  :value="progressData.currentPage || progressCategory.currentPage || 0"
                />
              </a-col>
              <a-col :span="8">
                <a-statistic
                  title="总页数"
                  :value="progressData.totalPages || 0"
                />
              </a-col>
              <a-col :span="8">
                <a-statistic
                  title="产品数量"
                  :value="`${progressData.crawledProducts || progressCategory.crawledProducts || 0} / ${progressData.totalProducts || progressCategory.totalProducts || 0}`"
                />
              </a-col>
            </a-row>
            <div v-if="progressData.lastUpdateTime" class="mt-2">
              <a-typography-text type="secondary">
                最后更新: {{ new Date(progressData.lastUpdateTime).toLocaleString('zh-CN') }}
              </a-typography-text>
            </div>
          </div>
        </a-card>

        <!-- 错误信息 -->
        <a-alert
          v-if="progressCategory.errorMessage"
          type="error"
          :message="progressCategory.errorMessage"
          show-icon
          class="mb-4"
        />

        <!-- 操作按钮 -->
        <div class="progress-actions">
          <a-space>
            <a-button @click="refreshProgress">
              <template #icon><ReloadOutlined /></template>
              刷新进度
            </a-button>
            <a-button type="primary" @click="closeProgressDialog">
              关闭
            </a-button>
          </a-space>
        </div>
      </div>
    </a-modal>

    <!-- P0-5: 编辑名称对话框 -->
    <a-modal
      v-model:open="showEditNameDialog"
      title="编辑分类名称"
      width="600px"
      @ok="handleSaveCategoryName"
      @cancel="resetEditingCategoryName"
    >
      <a-form :label-col="{ span: 6 }" :wrapper-col="{ span: 18 }">
        <a-form-item label="当前名称">
          <a-input :value="editingCategoryName.currentName" disabled />
        </a-form-item>
        <a-form-item label="API源名称" v-if="editingCategoryName.sourceName">
          <a-input :value="editingCategoryName.sourceName" disabled />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            这是来自立创API的原始名称，仅供参考
          </div>
        </a-form-item>
        <a-form-item label="自定义名称" required>
          <a-input
            v-model:value="editingCategoryName.customName"
            placeholder="输入自定义名称（中文）"
            allow-clear
          />
          <div style="font-size: 12px; color: #999; margin-top: 4px;">
            保存后，此名称将不会被API同步覆盖
          </div>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { message, Modal } from 'ant-design-vue'
import axios from 'axios'
import {
  SearchOutlined,
  ReloadOutlined,
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  UnorderedListOutlined,
  SettingOutlined,
  DatabaseOutlined,
  GlobalOutlined,
  ThunderboltOutlined,
  LineChartOutlined
} from '@ant-design/icons-vue'
import {
  getCategoryLevel1Page,
  getCategoryLevel1List,
  addCategoryLevel1,
  updateCategoryLevel1,
  deleteCategoryLevel1,
  getCategoryLevel2Page,
  getCategoryLevel2List,
  getCategoryLevel2ListByLevel1Id,
  addCategoryLevel2,
  updateCategoryLevel2,
  deleteCategoryLevel2,
  getCategoryLevel3List,
  getCategoryLevel3ListByLevel2Id,
  addCategoryLevel3,
  updateCategoryLevel3,
  deleteCategoryLevel3,
  updateShopCategoryCode
} from '@/api/category'
import { getShopList } from '@/api/shop'
import {
  patchAllCategoriesFromProducts,
  crawlAllCategories,
  patchProcessCategories,
  addSingleCategory,
  getCacheStats,
  clearCategoryCache,
  type CategoryCrawlResult,
  type CategoryPair
} from '@/api/categoryPatch'
import type { CategoryLevel1Code, CategoryLevel2Code, CategoryLevel3Code, Shop } from '@/types'

// V3 API Base URL
const API_BASE_V3 = '/api/v3/crawler'

// 响应式数据
const activeTab = ref('level1')
const level1Loading = ref(false)
const level2Loading = ref(false)
const level3Loading = ref(false)
const level1TableData = ref<CategoryLevel1Code[]>([])
const level2TableData = ref<CategoryLevel2Code[]>([])
const level3TableData = ref<CategoryLevel3Code[]>([])
const allLevel1Categories = ref<CategoryLevel1Code[]>([])
const allLevel2Categories = ref<CategoryLevel2Code[]>([])
const allShops = ref<Shop[]>([])
const showLevel1AddDialog = ref(false)
const showLevel2AddDialog = ref(false)
const showLevel3AddDialog = ref(false)
const showShopCodeDialog = ref(false)
// P0-5: 编辑名称对话框
const showEditNameDialog = ref(false)
const editingCategoryName = reactive({
  id: undefined as number | undefined,
  level: '' as 'level1' | 'level2' | 'level3',
  currentName: '',
  sourceName: '',
  customName: ''
})
const level1FormRef = ref()
const level2FormRef = ref()
const level3FormRef = ref()
const level3Level2Categories = ref<CategoryLevel2Code[]>([])  // 用于搜索表单的二级分类
const level3EditLevel2Categories = ref<CategoryLevel2Code[]>([])  // 用于编辑表单的二级分类
const selectedLevel2Category = ref<CategoryLevel2Code | null>(null)
const shopCodeList = ref<(Shop & { categoryCode: string })[]>([])

// 分类补丁相关数据
const showBatchPatchDialog = ref(false)
const showSingleAddDialog = ref(false)
const batchPatchLoading = ref(false)
const singleAddLoading = ref(false)
const batchPatchText = ref('')
const singleAddFormRef = ref()
const lastResult = ref<CategoryCrawlResult | null>(null)
const cacheStats = ref({
  level1CacheSize: 0,
  level2CacheSize: 0
})

// V3 爬虫相关数据
const showProgressDialog = ref(false)
const progressCategory = ref<CategoryLevel2Code | null>(null)
const progressData = reactive({
  currentPage: 0,
  totalPages: 0,
  crawledProducts: 0,
  totalProducts: 0,
  progress: 0,
  lastUpdateTime: null as string | null
})
let progressPollingTimer: ReturnType<typeof setInterval> | null = null

const patchLoading = reactive({
  fromProducts: false,
  crawlAll: false
})

const singleAddForm = reactive({
  level1Name: '',
  level2Name: ''
})

const level1SearchForm = reactive({
  categoryName: '',
  categoryCode: ''
})

const level2SearchForm = reactive({
  categoryName: '',
  categoryLevel1Id: undefined as number | undefined,
  crawlStatus: undefined as string | undefined
})

const level3SearchForm = reactive({
  categoryName: '',
  categoryLevel1Id: undefined as number | undefined,
  categoryLevel2Id: undefined as number | undefined,
  crawlStatus: undefined as string | undefined
})

const level1Pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const level2Pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const level3Pagination = reactive({
  current: 1,
  size: 20,
  total: 0
})

const editingLevel1 = reactive<CategoryLevel1Code>({
  categoryLevel1Name: '',
  categoryCode: ''
})

const editingLevel2 = reactive<CategoryLevel2Code>({
  categoryLevel2Name: '',
  categoryLevel1Id: 0
})

const editingLevel3 = reactive<CategoryLevel3Code>({
  categoryLevel3Name: '',
  catalogId: '',
  categoryLevel1Id: 0,
  categoryLevel2Id: 0
})

// 表单验证规则
const level1Rules = {
  categoryLevel1Name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' }
  ]
}

const level2Rules = {
  categoryLevel2Name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' }
  ],
  categoryLevel1Id: [
    { required: true, message: '请选择一级分类', trigger: 'change' }
  ]
}

const level3Rules = {
  categoryLevel3Name: [
    { required: true, message: '请输入分类名称', trigger: 'blur' }
  ],
  categoryLevel1Id: [
    { required: true, message: '请选择一级分类', trigger: 'change' }
  ],
  categoryLevel2Id: [
    { required: true, message: '请选择二级分类', trigger: 'change' }
  ],
  catalogId: [
    { required: true, message: '请输入目录ID', trigger: 'blur' }
  ]
}

const singleAddRules = {
  level1Name: [
    { required: true, message: '请输入一级分类名称', trigger: 'blur' }
  ],
  level2Name: [
    { required: true, message: '请输入二级分类名称', trigger: 'blur' }
  ]
}

// 方法
const fetchLevel1Data = async () => {
  level1Loading.value = true
  try {
    const params = {
      current: level1Pagination.current,
      size: level1Pagination.size,
      categoryName: level1SearchForm.categoryName,
      categoryCode: level1SearchForm.categoryCode
    }
    const result = await getCategoryLevel1Page(params)
    level1TableData.value = result.records
    level1Pagination.total = result.total
  } catch (error) {
    console.error('获取一级分类数据失败:', error)
  } finally {
    level1Loading.value = false
  }
}

const fetchLevel2Data = async () => {
  level2Loading.value = true
  try {
    const params = {
      current: level2Pagination.current,
      size: level2Pagination.size,
      categoryName: level2SearchForm.categoryName,
      categoryLevel1Id: level2SearchForm.categoryLevel1Id
    }
    const result = await getCategoryLevel2Page(params)
    level2TableData.value = result.records
    level2Pagination.total = result.total
  } catch (error) {
    console.error('获取二级分类数据失败:', error)
  } finally {
    level2Loading.value = false
  }
}

const fetchLevel3Data = async () => {
  level3Loading.value = true
  try {
    // 获取所有三级分类数据
    let allData: CategoryLevel3Code[] = []

    // 如果选择了二级分类，则获取该二级分类下的三级分类
    if (level3SearchForm.categoryLevel2Id) {
      allData = await getCategoryLevel3ListByLevel2Id(level3SearchForm.categoryLevel2Id)
    } else {
      // 否则获取所有三级分类
      allData = await getCategoryLevel3List()
    }

    // 前端筛选
    let filteredData = allData

    // 按分类名称筛选
    if (level3SearchForm.categoryName) {
      filteredData = filteredData.filter(item =>
        item.categoryLevel3Name.includes(level3SearchForm.categoryName)
      )
    }

    // 按一级分类筛选
    if (level3SearchForm.categoryLevel1Id) {
      filteredData = filteredData.filter(item =>
        item.categoryLevel1Id === level3SearchForm.categoryLevel1Id
      )
    }

    // 按爬取状态筛选
    if (level3SearchForm.crawlStatus) {
      filteredData = filteredData.filter(item =>
        item.crawlStatus === level3SearchForm.crawlStatus
      )
    }

    // 前端分页
    const total = filteredData.length
    const start = (level3Pagination.current - 1) * level3Pagination.size
    const end = start + level3Pagination.size
    const paginatedData = filteredData.slice(start, end)

    level3TableData.value = paginatedData
    level3Pagination.total = total
  } catch (error) {
    console.error('获取三级分类数据失败:', error)
    message.error('获取三级分类数据失败')
  } finally {
    level3Loading.value = false
  }
}

const loadAllLevel1Categories = async () => {
  try {
    allLevel1Categories.value = await getCategoryLevel1List()
  } catch (error) {
    console.error('获取一级分类列表失败:', error)
  }
}

const loadAllLevel2Categories = async () => {
  try {
    allLevel2Categories.value = await getCategoryLevel2List()
  } catch (error) {
    console.error('获取二级分类列表失败:', error)
  }
}

const loadAllShops = async () => {
  try {
    allShops.value = await getShopList()
  } catch (error) {
    console.error('获取店铺列表失败:', error)
  }
}

const handleTabClick = (key: string) => {
  if (key === 'level1') {
    fetchLevel1Data()
  } else if (key === 'level2') {
    fetchLevel2Data()
  } else if (key === 'level3') {
    fetchLevel3Data()
  }
}

// 一级分类相关方法
const handleLevel1Search = () => {
  level1Pagination.current = 1
  fetchLevel1Data()
}

const handleLevel1Reset = () => {
  level1SearchForm.categoryName = ''
  level1SearchForm.categoryCode = ''
  level1Pagination.current = 1
  fetchLevel1Data()
}

const handleLevel1Edit = (row: CategoryLevel1Code) => {
  Object.assign(editingLevel1, row)
  showLevel1AddDialog.value = true
}

const handleSaveLevel1 = async () => {
  if (!level1FormRef.value) return
  
  try {
    await level1FormRef.value.validate()
    
    if (editingLevel1.id) {
      await updateCategoryLevel1(editingLevel1.id, editingLevel1)
      message.success('更新一级分类成功')
    } else {
      await addCategoryLevel1(editingLevel1)
      message.success('新增一级分类成功')
    }
    
    showLevel1AddDialog.value = false
    resetEditingLevel1()
    fetchLevel1Data()
    loadAllLevel1Categories()
  } catch (error) {
    console.error('保存一级分类失败:', error)
  }
}

const resetEditingLevel1 = () => {
  Object.assign(editingLevel1, {
    id: undefined,
    categoryLevel1Name: '',
    categoryCode: ''
  })
}

const handleLevel1Delete = async (row: CategoryLevel1Code) => {
  Modal.confirm({
    title: '提示',
    content: '确定删除此一级分类吗？',
    onOk: async () => {
      try {
        await deleteCategoryLevel1(row.id!)
        message.success('删除成功')
        fetchLevel1Data()
        loadAllLevel1Categories()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

const viewLevel2Categories = (row: CategoryLevel1Code) => {
  level2SearchForm.categoryLevel1Id = row.id
  activeTab.value = 'level2'
  fetchLevel2Data()
}

// 二级分类相关方法
const handleLevel2Search = () => {
  level2Pagination.current = 1
  fetchLevel2Data()
}

const handleLevel2Reset = () => {
  level2SearchForm.categoryName = ''
  level2SearchForm.categoryLevel1Id = undefined
  level2SearchForm.crawlStatus = undefined
  level2Pagination.current = 1
  fetchLevel2Data()
}

const handleLevel2Edit = (row: CategoryLevel2Code) => {
  Object.assign(editingLevel2, row)
  showLevel2AddDialog.value = true
}

const handleSaveLevel2 = async () => {
  if (!level2FormRef.value) return
  
  try {
    await level2FormRef.value.validate()
    
    if (editingLevel2.id) {
      await updateCategoryLevel2(editingLevel2.id, editingLevel2)
      message.success('更新二级分类成功')
    } else {
      await addCategoryLevel2(editingLevel2)
      message.success('新增二级分类成功')
    }
    
    showLevel2AddDialog.value = false
    resetEditingLevel2()
    fetchLevel2Data()
  } catch (error) {
    console.error('保存二级分类失败:', error)
  }
}

const resetEditingLevel2 = () => {
  Object.assign(editingLevel2, {
    id: undefined,
    categoryLevel2Name: '',
    categoryLevel1Id: 0
  })
}

const handleLevel2Delete = async (row: CategoryLevel2Code) => {
  Modal.confirm({
    title: '提示',
    content: '确定删除此二级分类吗？',
    onOk: async () => {
      try {
        await deleteCategoryLevel2(row.id!)
        message.success('删除成功')
        fetchLevel2Data()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

const manageShopCodes = (row: CategoryLevel2Code) => {
  selectedLevel2Category.value = row
  
  // 解析现有的店铺分类码
  let existingCodes: Record<string, string> = {}
  if (row.shopCategoryCodes) {
    try {
      existingCodes = JSON.parse(row.shopCategoryCodes)
    } catch (error) {
      console.error('解析店铺分类码失败:', error)
    }
  }
  
  // 构建店铺分类码列表
  shopCodeList.value = allShops.value.map(shop => ({
    ...shop,
    categoryCode: existingCodes[shop.id?.toString() || ''] || ''
  }))
  
  showShopCodeDialog.value = true
}

const saveShopCode = async (shop: Shop & { categoryCode: string }) => {
  if (!selectedLevel2Category.value) return

  try {
    await updateShopCategoryCode(
      selectedLevel2Category.value.id!,
      shop.id!,
      shop.categoryCode
    )
    message.success('保存店铺分类码成功')
  } catch (error) {
    message.error('保存店铺分类码失败')
  }
}

// 三级分类相关方法
const handleLevel3Search = () => {
  level3Pagination.current = 1
  fetchLevel3Data()
}

const handleLevel3Reset = () => {
  level3SearchForm.categoryName = ''
  level3SearchForm.categoryLevel1Id = undefined
  level3SearchForm.categoryLevel2Id = undefined
  level3SearchForm.crawlStatus = undefined
  level3Pagination.current = 1
  level3Level2Categories.value = []
  fetchLevel3Data()
}

const handleLevel3Level1Change = async (categoryLevel1Id: number | undefined) => {
  level3SearchForm.categoryLevel2Id = undefined
  level3Level2Categories.value = []

  if (categoryLevel1Id) {
    try {
      level3Level2Categories.value = await getCategoryLevel2ListByLevel1Id(categoryLevel1Id)
    } catch (error) {
      console.error('获取二级分类失败:', error)
      message.error('获取二级分类失败')
    }
  }
}

const handleLevel3Edit = async (row: CategoryLevel3Code) => {
  Object.assign(editingLevel3, row)

  // 加载对应一级分类的二级分类列表
  if (row.categoryLevel1Id) {
    try {
      level3EditLevel2Categories.value = await getCategoryLevel2ListByLevel1Id(row.categoryLevel1Id)
    } catch (error) {
      console.error('获取二级分类失败:', error)
    }
  }

  showLevel3AddDialog.value = true
}

const handleLevel3EditLevel1Change = async (categoryLevel1Id: number) => {
  editingLevel3.categoryLevel2Id = 0
  level3EditLevel2Categories.value = []

  if (categoryLevel1Id) {
    try {
      level3EditLevel2Categories.value = await getCategoryLevel2ListByLevel1Id(categoryLevel1Id)
    } catch (error) {
      console.error('获取二级分类失败:', error)
      message.error('获取二级分类失败')
    }
  }
}

const handleSaveLevel3 = async () => {
  if (!level3FormRef.value) return

  try {
    await level3FormRef.value.validate()

    if (editingLevel3.id) {
      await updateCategoryLevel3(editingLevel3.id, editingLevel3)
      message.success('更新三级分类成功')
    } else {
      await addCategoryLevel3(editingLevel3)
      message.success('新增三级分类成功')
    }

    showLevel3AddDialog.value = false
    resetEditingLevel3()
    fetchLevel3Data()
  } catch (error) {
    console.error('保存三级分类失败:', error)
    message.error('保存三级分类失败')
  }
}

const resetEditingLevel3 = () => {
  Object.assign(editingLevel3, {
    id: undefined,
    categoryLevel3Name: '',
    catalogId: '',
    categoryLevel1Id: 0,
    categoryLevel2Id: 0
  })
  level3EditLevel2Categories.value = []
}

const handleLevel3Delete = async (row: CategoryLevel3Code) => {
  Modal.confirm({
    title: '提示',
    content: '确定删除此三级分类吗？',
    onOk: async () => {
      try {
        await deleteCategoryLevel3(row.id!)
        message.success('删除成功')
        fetchLevel3Data()
      } catch (error) {
        message.error('删除失败')
      }
    }
  })
}

const handleLevel3SizeChange = (current: number, size: number) => {
  level3Pagination.size = size
  level3Pagination.current = current
  fetchLevel3Data()
}

const handleLevel3CurrentChange = (current: number) => {
  level3Pagination.current = current
  fetchLevel3Data()
}

// P0-5: 编辑名称相关方法
const handleEditCategoryName = (record: any, level: 'level1' | 'level2' | 'level3') => {
  editingCategoryName.id = record.id
  editingCategoryName.level = level
  editingCategoryName.currentName = level === 'level1'
    ? record.categoryLevel1Name
    : level === 'level2'
    ? record.categoryLevel2Name
    : record.categoryLevel3Name
  editingCategoryName.sourceName = record.sourceName || ''
  editingCategoryName.customName = record.customName || editingCategoryName.currentName
  showEditNameDialog.value = true
}

const handleSaveCategoryName = async () => {
  if (!editingCategoryName.customName || !editingCategoryName.customName.trim()) {
    message.error('自定义名称不能为空')
    return
  }

  try {
    const apiPath = `/api/categories/${editingCategoryName.level}/${editingCategoryName.id}/customName`
    await axios.put(apiPath, {
      customName: editingCategoryName.customName.trim()
    })

    message.success('自定义名称保存成功')
    showEditNameDialog.value = false

    // 刷新对应的列表
    if (editingCategoryName.level === 'level1') {
      fetchLevel1Data()
      loadAllLevel1Categories()
    } else if (editingCategoryName.level === 'level2') {
      fetchLevel2Data()
    } else if (editingCategoryName.level === 'level3') {
      fetchLevel3Data()
    }
  } catch (error: any) {
    console.error('保存自定义名称失败:', error)
    message.error(error.response?.data?.message || '保存自定义名称失败')
  }
}

const resetEditingCategoryName = () => {
  Object.assign(editingCategoryName, {
    id: undefined,
    level: '',
    currentName: '',
    sourceName: '',
    customName: ''
  })
}

// 工具方法
const getLevel1Name = (categoryLevel1Id: number) => {
  const category = allLevel1Categories.value.find(item => item.id === categoryLevel1Id)
  return category ? category.categoryLevel1Name : '-'
}

const getLevel2Name = (categoryLevel2Id: number) => {
  const category = allLevel2Categories.value.find(item => item.id === categoryLevel2Id)
  return category ? category.categoryLevel2Name : '-'
}

const handleLevel1SizeChange = (current: number, size: number) => {
  level1Pagination.size = size
  level1Pagination.current = current
  fetchLevel1Data()
}

const handleLevel1CurrentChange = (current: number) => {
  level1Pagination.current = current
  fetchLevel1Data()
}

const handleLevel2SizeChange = (current: number, size: number) => {
  level2Pagination.size = size
  level2Pagination.current = current
  fetchLevel2Data()
}

const handleLevel2CurrentChange = (current: number) => {
  level2Pagination.current = current
  fetchLevel2Data()
}

const formatDateTime = (dateTime: string | undefined) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 分类补丁方法
const handlePatchFromProducts = async () => {
  patchLoading.fromProducts = true
  // 显示进度提示
  const hideLoading = message.loading('正在从产品数据中提取分类信息，这可能需要几分钟时间，请耐心等待...', 0)
  
  try {
    const result = await patchAllCategoriesFromProducts()
    lastResult.value = result
    
    if (result.success) {
      message.success(`一键补丁处理完成！处理了${result.processed}条数据，成功创建一级分类：${result.createdLevel1}个，二级分类：${result.createdLevel2}个`)
      refreshStats()
      if (activeTab.value === 'level1') {
        fetchLevel1Data()
        loadAllLevel1Categories()
      } else if (activeTab.value === 'level2') {
        fetchLevel2Data()
      }
    } else {
      message.error('一键补丁处理失败: ' + (result.errorMessage || '未知错误'))
    }
  } catch (error: any) {
    console.error('一键补丁处理失败:', error)
    if (error.code === 'ECONNABORTED') {
      message.error('请求超时，数据处理操作可能需要更长时间。请稍后查看结果或重试。')
    } else if (error.response?.status) {
      message.error(`服务器错误（${error.response.status}）：${error.response.data?.message || '请求失败'}`)
    } else {
      message.error('一键补丁处理失败，请检查网络连接或联系管理员')
    }
  } finally {
    hideLoading()
    patchLoading.fromProducts = false
  }
}

const handleCrawlAll = async () => {
  patchLoading.crawlAll = true
  // 显示进度提示
  const hideLoading = message.loading('正在爬取官方分类数据，这可能需要几分钟时间，请耐心等待...', 0)
  
  try {
    const result = await crawlAllCategories()
    lastResult.value = result
    
    if (result.success) {
      message.success(`爬取官方分类完成！成功创建一级分类：${result.createdLevel1}个，二级分类：${result.createdLevel2}个`)
      refreshStats()
      if (activeTab.value === 'level1') {
        fetchLevel1Data()
        loadAllLevel1Categories()
      } else if (activeTab.value === 'level2') {
        fetchLevel2Data()
      }
    } else {
      message.error('爬取官方分类失败: ' + (result.errorMessage || '未知错误'))
    }
  } catch (error: any) {
    console.error('爬取官方分类失败:', error)
    if (error.code === 'ECONNABORTED') {
      message.error('请求超时，爬取分类操作可能需要更长时间。请稍后查看结果或重试。')
    } else if (error.response?.status) {
      message.error(`服务器错误（${error.response.status}）：${error.response.data?.message || '请求失败'}`)
    } else {
      message.error('爬取官方分类失败，请检查网络连接或联系管理员')
    }
  } finally {
    hideLoading()
    patchLoading.crawlAll = false
  }
}

const handleBatchPatch = async () => {
  if (!batchPatchText.value.trim()) {
    message.error('请输入要处理的分类数据')
    return
  }

  batchPatchLoading.value = true
  try {
    // 解析文本为分类组合
    const lines = batchPatchText.value.split('\n').filter(line => line.trim())
    const categoryPairs: CategoryPair[] = []
    
    for (const line of lines) {
      const parts = line.split(',').map(s => s.trim())
      if (parts.length === 2) {
        categoryPairs.push({
          level1Name: parts[0],
          level2Name: parts[1]
        })
      }
    }
    
    if (categoryPairs.length === 0) {
      message.error('没有有效的分类数据，请检查格式')
      return
    }
    
    const result = await patchProcessCategories(categoryPairs)
    lastResult.value = result
    
    if (result.success) {
      message.success(`批量处理完成，处理了${result.processed}个分类组合`)
      showBatchPatchDialog.value = false
      batchPatchText.value = ''
      refreshStats()
      if (activeTab.value === 'level1') {
        fetchLevel1Data()
        loadAllLevel1Categories()
      } else if (activeTab.value === 'level2') {
        fetchLevel2Data()
      }
    } else {
      message.error('批量处理失败: ' + (result.errorMessage || '未知错误'))
    }
  } catch (error) {
    console.error('批量处理失败:', error)
    message.error('批量处理失败，请查看控制台获取详细信息')
  } finally {
    batchPatchLoading.value = false
  }
}

const handleSingleAdd = async () => {
  if (!singleAddFormRef.value) return
  
  try {
    await singleAddFormRef.value.validate()
    
    singleAddLoading.value = true
    
    await addSingleCategory(
      singleAddForm.level1Name, 
      singleAddForm.level2Name
    )
    
    message.success('添加分类成功')
    showSingleAddDialog.value = false
    resetSingleAddForm()
    refreshStats()
    
    if (activeTab.value === 'level1') {
      fetchLevel1Data()
      loadAllLevel1Categories()
    } else if (activeTab.value === 'level2') {
      fetchLevel2Data()
    }
  } catch (error) {
    console.error('添加分类失败:', error)
    message.error('添加分类失败，请查看控制台获取详细信息')
  } finally {
    singleAddLoading.value = false
  }
}

const loadSampleData = () => {
  batchPatchText.value = `电阻,贴片电阻
电容,陶瓷电容
集成电路,运算放大器
二极管,发光二极管
三极管,N沟道MOSFET
连接器,端子
晶振,石英晶振
开关,轻触开关
传感器,温度传感器
电感,功率电感`
}

const clearBatchPatchText = () => {
  batchPatchText.value = ''
}

const resetSingleAddForm = () => {
  Object.assign(singleAddForm, {
    level1Name: '',
    level2Name: ''
  })
}

const refreshStats = async () => {
  try {
    const stats = await getCacheStats()
    cacheStats.value = stats
  } catch (error) {
    console.error('获取统计信息失败:', error)
  }
}

const handleClearCache = async () => {
  Modal.confirm({
    title: '确认操作',
    content: '确定要清空分类缓存吗？这将清空内存中的分类ID映射缓存。',
    onOk: async () => {
      try {
        await clearCategoryCache()
        message.success('缓存清空成功')
        refreshStats()
      } catch (error) {
        console.error('清空缓存失败:', error)
        message.error('清空缓存失败')
      }
    }
  })
}

// V3 爬虫方法
const startCategoryCrawl = async (category: CategoryLevel2Code) => {
  try {
    // 设置loading状态
    category._crawling = true

    const { data } = await axios.post(`${API_BASE_V3}/start-category/${category.id}`)

    if (data.code === 200) {
      message.success(`已创建高优先级爬取任务：${category.categoryLevel2Name}`)
      // 刷新列表
      setTimeout(() => {
        fetchLevel2Data()
      }, 1000)
    } else {
      message.error(data.message || '创建爬取任务失败')
    }
  } catch (error: any) {
    console.error('创建爬取任务失败:', error)
    message.error(error.response?.data?.message || '创建爬取任务失败')
  } finally {
    category._crawling = false
  }
}

const viewCrawlProgress = async (category: CategoryLevel2Code) => {
  progressCategory.value = category
  showProgressDialog.value = true

  // 立即获取一次进度
  await refreshProgress()

  // 开始轮询进度（每3秒）
  if (progressPollingTimer) {
    clearInterval(progressPollingTimer)
  }
  progressPollingTimer = setInterval(() => {
    refreshProgress()
  }, 3000)
}

const closeProgressDialog = () => {
  showProgressDialog.value = false
  progressCategory.value = null

  // 停止轮询
  if (progressPollingTimer) {
    clearInterval(progressPollingTimer)
    progressPollingTimer = null
  }

  // 刷新列表
  fetchLevel2Data()
}

const refreshProgress = async () => {
  if (!progressCategory.value?.id) return

  try {
    const { data } = await axios.get(`${API_BASE_V3}/progress/category/${progressCategory.value.id}`)

    if (data.code === 200 && data.data) {
      // 更新进度数据
      Object.assign(progressData, {
        currentPage: parseInt(data.data.currentPage) || 0,
        totalPages: parseInt(data.data.totalPages) || 0,
        crawledProducts: parseInt(data.data.crawledProducts) || 0,
        totalProducts: parseInt(data.data.totalProducts) || 0,
        progress: parseInt(data.data.progress) || 0,
        lastUpdateTime: data.data.lastUpdateTime
      })

      // 也更新progressCategory以便显示最新状态
      if (progressCategory.value) {
        progressCategory.value.crawlProgress = progressData.progress
        progressCategory.value.crawledProducts = progressData.crawledProducts
        progressCategory.value.totalProducts = progressData.totalProducts
        progressCategory.value.currentPage = progressData.currentPage
      }
    }
  } catch (error: any) {
    console.error('获取进度失败:', error)
  }
}

const getCrawlStatusBadge = (status: string | undefined): 'success' | 'processing' | 'default' | 'error' => {
  switch (status) {
    case 'completed':
      return 'success'
    case 'processing':
      return 'processing'
    case 'failed':
      return 'error'
    default:
      return 'default'
  }
}

const getCrawlStatusText = (status: string | undefined): string => {
  switch (status) {
    case 'pending':
      return '待爬取'
    case 'processing':
      return '爬取中'
    case 'completed':
      return '已完成'
    case 'failed':
      return '失败'
    default:
      return '未爬取'
  }
}

const getProgressStatus = (crawlStatus: string | undefined): 'success' | 'active' | 'exception' | 'normal' => {
  switch (crawlStatus) {
    case 'completed':
      return 'success'
    case 'processing':
      return 'active'
    case 'failed':
      return 'exception'
    default:
      return 'normal'
  }
}

// 生命周期
onMounted(() => {
  fetchLevel1Data()
  loadAllLevel1Categories()
  loadAllLevel2Categories()
  loadAllShops()
  refreshStats()
})

onUnmounted(() => {
  // 清理轮询定时器
  if (progressPollingTimer) {
    clearInterval(progressPollingTimer)
  }
})
</script>

<style scoped>
.category-management {
  padding: 0;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-4 {
  margin-top: 16px;
}

.text-right {
  text-align: right;
}

.search-actions {
  margin-top: 16px;
  text-align: right;
}

.search-actions .ant-btn {
  margin-left: 8px;
}

/* 分类补丁样式 */
.patch-section {
  text-align: center;
}

.patch-section h3 {
  margin-bottom: 8px;
  color: #1890ff;
}

.text-muted {
  color: #666;
  margin-bottom: 20px;
}

.patch-actions {
  margin: 24px 0;
}

.patch-help {
  margin-top: 24px;
  text-align: left;
}

.patch-help ul {
  margin: 8px 0 0 20px;
}

.patch-help li {
  margin-bottom: 4px;
}

.patch-stats h4,
.patch-result h4 {
  margin-bottom: 16px;
  color: #262626;
}

.batch-patch-form p {
  margin-bottom: 12px;
  color: #666;
}

/* V3 爬虫进度对话框样式 */
.progress-dialog .progress-item h4 {
  margin-bottom: 12px;
  font-weight: 500;
}

.progress-dialog .progress-details {
  padding: 8px 0;
}

.progress-dialog .progress-actions {
  text-align: right;
  margin-top: 16px;
}

.mt-2 {
  margin-top: 8px;
}
</style>