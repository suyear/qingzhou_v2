<template>
  <main class="step-editor">
    <div class="editor-head">
      <div class="head-main">
        <h2 class="editor-title">编排步骤</h2>
        <p class="editor-desc">{{ chainNodes.length ? `共 ${chainNodes.length} 步，${configuredCount} 步已配置` : '从下方选择接口开始' }}</p>
        <el-progress
          v-if="chainNodes.length"
          :percentage="progressPercent"
          :stroke-width="8"
          :status="allDone ? 'success' : undefined"
          class="progress-bar"
        />
      </div>
      <div class="head-actions">
        <el-button
          v-if="chainNodes.length"
          size="small"
          :type="canvasVisible ? 'primary' : 'default'"
          @click="emit('toggle-canvas')"
        >
          {{ canvasVisible ? '收起流程图' : '流程图预览' }}
        </el-button>
        <el-tooltip :disabled="canTryRun || !chainNodes.length" :content="tryRunHint || '请先完成配置'" placement="bottom">
          <span v-if="chainNodes.length" class="head-btn-wrap">
            <el-button type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
          </span>
        </el-tooltip>
        <el-button type="primary" @click="focusQuickAdd">+ 添加步骤</el-button>
      </div>
    </div>

    <div class="editor-body">
      <div class="left-column">
        <div class="left-tabs">
          <button
            type="button"
            class="left-tab"
            :class="{ active: leftTab === 'steps' }"
            @click="leftTab = 'steps'"
          >
            步骤 ({{ chainNodes.length }})
          </button>
          <button
            type="button"
            class="left-tab"
            :class="{ active: leftTab === 'inputs' }"
            @click="leftTab = 'inputs'"
          >
            调用时填 ({{ inputFields.length }})
          </button>
          <button
            type="button"
            class="left-tab"
            :class="{ active: leftTab === 'outputs' }"
            @click="leftTab = 'outputs'"
          >
            最后返回
          </button>
          <button
            type="button"
            class="left-tab"
            :class="{ active: leftTab === 'add' }"
            @click="leftTab = 'add'"
          >
            添加接口
          </button>
        </div>

        <div ref="leftScrollRef" class="left-scroll">
          <nav v-show="leftTab === 'steps'" class="step-timeline" aria-label="步骤列表">
            <div v-if="!chainNodes.length" class="timeline-empty">
              <p>还没有步骤</p>
              <el-button type="primary" size="small" @click="leftTab = 'add'">去添加接口</el-button>
            </div>
            <p v-else class="timeline-tip">点击选中配置；卡片间 ⊕ 可插入；↑↓ 调顺序</p>

            <template v-for="(item, index) in chainNodes" :key="item.id">
              <div
                :id="`step-${item.id}`"
                class="timeline-item"
                :class="{
                  active: selectedId === item.id,
                  done: item.configured,
                  pending: !item.configured && item.fieldCount,
                }"
              >
                <div v-if="index > 0" class="timeline-line" :class="{ linked: item.deps?.length }" />
                <button
                  type="button"
                  class="timeline-main"
                  @click="selectStep(item.id)"
                >
                  <span class="timeline-index">
                    <span v-if="item.configured" class="check">✓</span>
                    <span v-else>{{ index + 1 }}</span>
                  </span>
                  <span class="timeline-content">
                    <span class="timeline-title-row">
                      <span class="timeline-title">{{ item.name }}</span>
                      <el-tag v-if="item.configured" size="small" type="success" effect="plain">完成</el-tag>
                      <el-tag v-else-if="item.fieldCount" size="small" type="warning" effect="plain">待填</el-tag>
                    </span>
                    <span class="timeline-sub">{{ item.method }} {{ item.path }}</span>
                    <span v-if="item.deps?.length" class="timeline-deps">
                      <span
                        v-for="dep in item.deps.slice(0, 3)"
                        :key="`${dep.fromNode}:${dep.fromSource || 'output'}`"
                        class="dep-chip"
                      >
                        {{ dep.label }}
                      </span>
                    </span>
                    <span v-else-if="index > 0 && !item.configured" class="timeline-deps muted">还有必填项没接上</span>
                    <span v-if="item.summaryLines?.length" class="timeline-summary">
                      <span
                        v-for="line in item.summaryLines.slice(0, 2)"
                        :key="line.key"
                        class="summary-chip"
                        :class="{ ok: line.done }"
                      >
                        {{ line.label }}：{{ line.text }}
                      </span>
                    </span>
                  </span>
                </button>
                <div class="timeline-ops">
                  <button
                    v-if="index > 0"
                    type="button"
                    class="op-btn"
                    title="上移"
                    @click="emit('move', item.id, -1)"
                  >
                    ↑
                  </button>
                  <button
                    v-if="index < chainNodes.length - 1"
                    type="button"
                    class="op-btn"
                    title="下移"
                    @click="emit('move', item.id, 1)"
                  >
                    ↓
                  </button>
                  <el-popconfirm title="删除此步骤？" @confirm="emit('remove', item.id)">
                    <template #reference>
                      <button type="button" class="op-btn danger" title="删除">×</button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
              <button
                type="button"
                class="timeline-insert"
                :title="`在第 ${index + 1} 步后插入`"
                @click="beginInsertAfter(item.id, index)"
              >
                <span class="insert-plus">⊕</span>
                <span class="insert-text">插入下一步</span>
              </button>
            </template>
          </nav>

          <section v-show="leftTab === 'inputs'" class="inputs-panel">
            <p class="quick-hint">调用这个工作流时，外面要准备的内容会自动汇总到这里。可以改说明和是否必填；要增减，请到对应步骤里选「调用时传入」。</p>
            <div v-if="!inputFields.length" class="inputs-empty">
              <p>调用时暂时不用额外填写</p>
              <p class="inputs-empty-sub">在某一步选「调用时传入」后，会出现在这里</p>
            </div>
            <div v-for="(row, index) in inputFields" :key="`${row.key}-${index}`" class="input-row">
              <el-input
                :model-value="row.key"
                placeholder="参数键"
                class="input-key"
                disabled
              />
              <el-input
                :model-value="row.description"
                placeholder="说明（选填）"
                class="input-desc"
                @input="(val) => patchInputField(index, { description: val })"
              />
              <el-checkbox
                :model-value="row.required"
                @change="(val) => patchInputField(index, { required: val })"
              >必填</el-checkbox>
            </div>
          </section>

          <section v-show="leftTab === 'outputs'" class="inputs-panel">
            <p class="quick-hint">调用方最后拿到什么。默认把每一步交出的内容按步骤名放在一起，一般不用改。试运行和开放 API 看到的是同一种结果。</p>
            <el-alert
              v-if="workflowStatus === 'PUBLISHED'"
              type="warning"
              :closable="false"
              show-icon
              class="output-alert"
              :title="workflowDirty
                ? '草稿出参已改，开放/调度仍用已发布快照，需重新发布才生效'
                : `开放/调度使用已发布快照；试运行走当前草稿`"
            />
            <div class="output-mode">
              <el-radio-group :model-value="outputSchema.mode || 'merge'" @change="onOutputModeChange">
                <el-radio-button value="merge">每步结果都返回</el-radio-button>
                <el-radio-button value="fields">只挑几个字段</el-radio-button>
                <el-radio-button value="firstRow">只要查询的第一行</el-radio-button>
                <el-radio-button value="last">只要最后一步</el-radio-button>
              </el-radio-group>
            </div>
            <p v-if="(outputSchema.mode || 'merge') === 'merge'" class="inputs-empty-sub">
              外面会按步骤名拿到每一步交出的内容。下面改的是给别人看的名字。
            </p>
            <template v-if="(outputSchema.mode || 'merge') === 'merge'">
              <div v-if="!allStepSources.length" class="inputs-empty">
                <p>还没有步骤</p>
                <p class="inputs-empty-sub">添加步骤后，在这里给每一步交出的内容起名字</p>
              </div>
              <div
                v-for="(step, si) in allStepSources"
                :key="step.id"
                class="merge-ports-block"
              >
                <button
                  type="button"
                  class="merge-step-head"
                  @click="emit('select', step.id)"
                >
                  <span>第 {{ si + 1 }} 步 · {{ step.name }}</span>
                  <span class="section-hint">{{ (step.outputPorts || []).length }} 个字段</span>
                </button>
                <div
                  v-for="(port, pi) in (step.outputPorts || [])"
                  :key="`${step.id}-${pi}-${port.key}`"
                  class="input-row merge-port-row"
                >
                  <el-input
                    :model-value="port.key"
                    placeholder="字段名"
                    class="sout-key"
                    @change="(val) => commitPortKeyForStep(step.id, pi, val)"
                  >
                    <template #prefix>
                      <span class="field-label-prefix">名字</span>
                    </template>
                  </el-input>
                  <el-select
                    :model-value="stepOutputPathValue(port)"
                    filterable
                    allow-create
                    default-first-option
                    clearable
                    placeholder="从结果里取哪一项"
                    class="sout-path"
                    @change="(val) => patchPortForStep(step.id, pi, { fromPath: pathFromSelect(val) })"
                    @clear="() => patchPortForStep(step.id, pi, { fromPath: '' })"
                  >
                    <el-option value="*" label="整份结果" />
                    <el-option
                      v-for="name in (step.responseFields || [])"
                      :key="`${step.id}-rf-${name}`"
                      :value="name"
                      :label="fieldOptionLabel(name)"
                    />
                  </el-select>
                  <el-input
                    :model-value="port.description"
                    placeholder="说明"
                    class="sout-desc"
                    @input="(val) => patchPortForStep(step.id, pi, { description: val })"
                  />
                  <button
                    type="button"
                    class="op-btn danger"
                    title="删除字段"
                    :disabled="(step.outputPorts || []).length <= 1"
                    @click="removePortForStep(step.id, pi)"
                  >×</button>
                </div>
                <div
                  v-if="showAddMergeOutput === step.id"
                  class="add-param-form merge-add-form"
                >
                  <el-input
                    v-model="outputDraft.key"
                    placeholder="字段名，如 user、username"
                    @keyup.enter="confirmAddOutput"
                  />
                  <el-select
                    v-model="outputDraft.fromPath"
                    filterable
                    allow-create
                    default-first-option
                    clearable
                    placeholder="从结果里取哪一项（空=整份结果）"
                    style="width: 100%"
                  >
                    <el-option value="" label="整份结果" />
                    <el-option
                      v-for="name in (step.responseFields || [])"
                      :key="`draft-${step.id}-${name}`"
                      :value="name"
                      :label="fieldOptionLabel(name)"
                    />
                  </el-select>
                  <el-input v-model="outputDraft.description" placeholder="说明（选填）" />
                  <div class="add-param-actions">
                    <el-button type="primary" @click="confirmAddOutput">添加</el-button>
                    <el-button @click="cancelAddOutput">取消</el-button>
                  </div>
                </div>
                <el-button
                  v-else
                  type="primary"
                  plain
                  size="small"
                  class="add-input-btn"
                  @click="openAddOutput(step.id)"
                >+ 添加字段</el-button>
              </div>
            </template>
            <p v-else-if="outputSchema.mode === 'firstRow'" class="inputs-empty-sub">
              适合「按 ID 查单条」：从末步查询结果的 <code>rows[0]</code> 取出对象直接对外返回。
            </p>
            <p v-else-if="outputSchema.mode === 'last'" class="inputs-empty-sub">
              对外返回{{ lastStepName ? `第末步「${lastStepName}」` : '最后一步' }}的完整结果。
            </p>
            <template v-else-if="outputSchema.mode === 'fields'">
              <p class="inputs-empty-sub">从「按步骤合并」结果中投影字段；左侧填<strong>对外字段名</strong>。</p>
              <div v-if="!(outputSchema.fields || []).length" class="inputs-empty">
                <p>还没有整形字段</p>
                <p class="inputs-empty-sub warn">未配置时保存将回落为「按步骤合并」</p>
              </div>
              <div
                v-for="(row, index) in (outputSchema.fields || [])"
                :key="`out-${index}`"
                class="input-row merge-port-row"
              >
                <el-input
                  :model-value="row.key"
                  placeholder="对外字段名"
                  class="sout-key"
                  @change="(val) => commitReshapeFieldKey(index, val)"
                >
                  <template #prefix>
                    <span class="field-label-prefix">字段名</span>
                  </template>
                </el-input>
                <el-select
                  :model-value="reshapeSourceValue(row)"
                  filterable
                  allow-create
                  default-first-option
                  placeholder="来自合并结果：步骤.端口 或手输 $.路径"
                  class="sout-path"
                  @change="(val) => onReshapeSourceChange(index, val)"
                >
                  <template v-for="(step, si) in allStepSources" :key="step.id">
                    <el-option-group :label="`第 ${si + 1} 步 · ${step.name}`">
                      <el-option
                        v-for="port in (step.outputPorts || [{ key: 'result', fromPath: '' }])"
                        :key="`${step.name}:${port.key}`"
                        :value="`merge:${step.name}:${port.key}`"
                        :label="`${port.key}`"
                      />
                      <el-option
                        :value="`merge:${step.name}:*`"
                        label="整步出参对象"
                      />
                    </el-option-group>
                  </template>
                </el-select>
                <el-input
                  :model-value="row.description"
                  placeholder="说明（选填）"
                  class="sout-desc"
                  @input="(val) => patchOutputField(index, { description: val })"
                />
                <button type="button" class="op-btn danger" title="删除" @click="removeOutputField(index)">×</button>
              </div>
              <el-button
                type="primary"
                plain
                class="add-input-btn"
                :disabled="!allStepSources.length"
                @click="addReshapeField"
              >+ 添加整形字段</el-button>
            </template>

            <div class="output-preview">
              <div class="output-preview-title">预计返回形状</div>
              <pre class="output-preview-code">{{ outputShapeText }}</pre>
              <template v-if="recentPublicOutput !== undefined && recentPublicOutput !== null">
                <div class="output-preview-title mt">最近试跑 · 对外出参</div>
                <pre class="output-preview-code">{{ recentOutputText }}</pre>
              </template>
            </div>
          </section>

          <section v-show="leftTab === 'add'" ref="quickAddRef" class="quick-add">
            <p class="quick-hint">{{ addHint }}</p>
            <el-alert
              v-if="pendingInsertAfterId"
              type="info"
              :closable="true"
              show-icon
              class="insert-alert"
              :title="insertHintTitle"
              @close="pendingInsertAfterId = null"
            />
            <el-input
              v-model="pickerKeyword"
              size="default"
              placeholder="搜索接口名称 / 编码"
              clearable
              class="quick-search"
            />
            <div v-if="!components.length" class="quick-empty">
              <p>还没有接口组件</p>
              <el-button type="primary" size="small" @click="router.push('/components')">去接入接口</el-button>
            </div>
            <div v-else-if="!groupedComponents.length" class="quick-empty">没有匹配的接口</div>
            <div v-else>
              <div v-for="group in groupedComponents" :key="group.key" class="quick-group">
                <div class="quick-group-name">{{ group.label }}</div>
                <div class="quick-list">
                  <button
                    v-for="item in group.items"
                    :key="item.id"
                    type="button"
                    class="quick-item"
                    @click="pickComponent(item)"
                  >
                    <span class="quick-main">
                      <span class="quick-name">{{ item.componentName }}</span>
                      <span class="quick-sub">{{ item.httpMethod }} {{ item.urlPath || item.urlTemplate }}</span>
                    </span>
                    <span class="quick-add-btn">添加</span>
                  </button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </div>

      <section class="step-config">
        <template v-if="!selectedId && chainNodes.length">
          <div class="config-placeholder">
            <p>点击左侧步骤卡片配置参数</p>
            <el-button type="primary" @click="selectFirstPending">打开待配置步骤</el-button>
          </div>
        </template>

        <template v-else-if="!chainNodes.length">
          <div class="config-placeholder">
            <p>切换到「添加接口」选择第一个步骤</p>
            <el-button type="primary" @click="leftTab = 'add'">添加接口</el-button>
          </div>
        </template>

        <template v-else>
          <div class="config-head">
            <div class="config-badge">第 {{ selectedIndex + 1 }} 步 / 共 {{ chainNodes.length }} 步</div>
            <h3 class="config-title">{{ nodeName || '未命名步骤' }}</h3>
            <p v-if="selectedNode" class="config-sub">{{ selectedNode.method }} {{ selectedNode.path }}</p>
          </div>

          <div class="step-toolbar">
            <el-button :disabled="selectedIndex <= 0" @click="goStep(-1)">← 上一步</el-button>
            <el-button @click="goStep(1)">{{ nextStepLabel }}</el-button>
            <el-button :disabled="selectedIndex <= 0" @click="emit('move', selectedId, -1)">上移</el-button>
            <el-button :disabled="selectedIndex >= chainNodes.length - 1" @click="emit('move', selectedId, 1)">下移</el-button>
            <el-popconfirm title="确定删除此步骤？" @confirm="emit('remove', selectedId)">
              <template #reference>
                <el-button type="danger" plain>删除</el-button>
              </template>
            </el-popconfirm>
          </div>

          <div v-if="selectedIndex > 0 && fields.length" class="handoff-card">
            <div class="handoff-head">
              <div>
                <div class="section-title">和前面怎么接</div>
                <p class="handoff-lead">每一项可以自己填、调用时由外面传入，或直接用前面某一步交出的结果。</p>
              </div>
              <el-button size="small" type="primary" plain @click="emit('auto-bind-upstream')">同名自动接上</el-button>
            </div>
            <ul v-if="requiredFields.length" class="handoff-list">
              <li v-for="field in requiredFields" :key="`hand-${field.key}`">
                <span class="handoff-name">{{ fieldLabel(field) }}</span>
                <span
                  class="handoff-text"
                  :class="{ ok: isFieldConfigured(findBinding(field.key), field) }"
                >{{ bindingSummary(findBinding(field.key), field, bindingCtx) }}</span>
              </li>
            </ul>
          </div>

          <el-form label-position="top" size="default" class="config-form">
            <el-form-item label="步骤名称">
              <el-input
                :model-value="nodeName"
                placeholder="例如：查询客户、发送通知"
                @input="(val) => emit('update-name', val)"
              />
            </el-form-item>
          </el-form>

          <div v-if="!fields.length" class="config-empty">
            <el-alert type="success" :closable="false" show-icon title="这一步没有必须填写的内容。下面仍可以设置要交给后面的结果。" />
            <div class="empty-actions">
              <el-button @click="showAddCustom = true">+ 再加一项</el-button>
              <el-button type="primary" @click="focusQuickAdd">+ 添加下一步</el-button>
              <el-button type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
            </div>
          </div>

          <div v-else class="config-fields">
            <div v-if="requiredFields.length" class="field-section">
              <div class="section-head">
                <span class="section-title">这一步需要</span>
                <span class="section-hint">{{ requiredDone }}/{{ requiredFields.length }} 已完成</span>
              </div>
              <WorkflowParamField
                v-for="field in requiredFields"
                :key="field.key"
                :field="field"
                :binding="findBinding(field.key)"
                :upstream-sources="upstreamSources"
                :input-fields="inputFields"
                @change="(patch) => patchBinding(field.key, patch)"
                @remove="removeCustomField"
              />
            </div>

            <div v-if="optionalFields.length" class="field-section">
              <button type="button" class="section-toggle" @click="showOptional = !showOptional">
                <span class="section-title">可以不填</span>
                <span class="section-hint">{{ optionalFields.length }} 项 · {{ showOptional ? '收起' : '展开' }}</span>
              </button>
              <template v-if="showOptional">
                <WorkflowParamField
                  v-for="field in optionalFields"
                  :key="field.key"
                  :field="field"
                  :binding="findBinding(field.key)"
                  :upstream-sources="upstreamSources"
                  :input-fields="inputFields"
                  @change="(patch) => patchBinding(field.key, patch)"
                  @remove="removeCustomField"
                />
              </template>
            </div>

            <div class="add-param-block">
              <el-button v-if="!showAddCustom" @click="showAddCustom = true">+ 再加一项</el-button>
              <div v-else class="add-param-form">
                <el-input v-model="customDraft.key" placeholder="参数键，如 deptId" />
                <el-input v-model="customDraft.description" placeholder="说明（选填）" />
                <el-checkbox v-model="customDraft.required">必填</el-checkbox>
                <div class="add-param-actions">
                  <el-button type="primary" @click="confirmAddCustom">添加</el-button>
                  <el-button @click="cancelAddCustom">取消</el-button>
                </div>
              </div>
            </div>
          </div>

          <div class="field-section step-outputs-section">
              <div class="section-head">
                <span class="section-title">交给后面的内容</span>
                <span class="section-hint">后面的步骤会按这些名字来用</span>
              </div>
              <p class="section-hint soft">不需要拆开时，选「整份结果」即可。试运行后，这里会出现结果里的具体项目。</p>
              <div class="sout-col-head" aria-hidden="true">
                <span>交给后面的名字</span>
                <span>从结果里取</span>
                <span>说明</span>
                <span></span>
              </div>
              <div
                v-for="(row, index) in stepOutputs"
                :key="`sout-${index}-${row.key}`"
                class="input-row step-output-row"
              >
                <el-input
                  :model-value="row.key"
                  placeholder="如 user、username"
                  class="sout-key"
                  @change="(val) => commitStepOutputKey(index, val)"
                />
                <el-select
                  :model-value="stepOutputPathValue(row)"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  placeholder="从这一步的结果里取（空=整份结果）"
                  class="sout-path"
                  @change="(val) => onStepOutputPathChange(index, val)"
                  @clear="() => onStepOutputPathChange(index, '*')"
                >
                  <el-option value="*" label="整份结果" />
                  <el-option
                    v-for="name in selectedResponseFields"
                    :key="name"
                    :value="name"
                    :label="fieldOptionLabel(name)"
                  />
                </el-select>
                <el-input
                  :model-value="row.description"
                  placeholder="说明（选填）"
                  class="sout-desc"
                  @input="(val) => patchStepOutput(index, { description: val })"
                />
                <button
                  type="button"
                  class="op-btn danger"
                  title="删除此出参"
                  :disabled="stepOutputs.length <= 1"
                  @click="removeStepOutput(index)"
                >×</button>
              </div>
              <p v-if="stepOutputs.length <= 1" class="section-hint soft">至少留一项交给后面。名字用英文，方便下一步直接选。</p>
              <div v-if="showAddOutput && !outputDraft.nodeId" class="add-param-form">
                <el-input
                  ref="outputDraftKeyRef"
                  v-model="outputDraft.key"
                  placeholder="字段名，如 user、username"
                  @keyup.enter="confirmAddOutput"
                />
                <el-select
                  v-model="outputDraft.fromPath"
                  filterable
                  allow-create
                  default-first-option
                  clearable
                  placeholder="从结果里取哪一项（空=整份结果）"
                  style="width: 100%"
                >
                  <el-option value="" label="整份结果" />
                  <el-option
                    v-for="name in selectedResponseFields"
                    :key="`draft-sel-${name}`"
                    :value="name"
                    :label="fieldOptionLabel(name)"
                  />
                </el-select>
                <el-input v-model="outputDraft.description" placeholder="说明（选填）" />
                <div class="add-param-actions">
                  <el-button type="primary" @click="confirmAddOutput">添加</el-button>
                  <el-button @click="cancelAddOutput">取消</el-button>
                </div>
              </div>
              <el-button
                v-else
                type="primary"
                plain
                class="add-input-btn"
                @click="openAddOutput()"
              >+ 再交一项给后面</el-button>
          </div>

          <div v-if="!fields.length && showAddCustom" class="add-param-block">
            <div class="add-param-form">
              <el-input v-model="customDraft.key" placeholder="参数键，如 deptId" />
              <el-input v-model="customDraft.description" placeholder="说明（选填）" />
              <el-checkbox v-model="customDraft.required">必填</el-checkbox>
              <div class="add-param-actions">
                <el-button type="primary" @click="confirmAddCustom">添加</el-button>
                <el-button @click="cancelAddCustom">取消</el-button>
              </div>
            </div>
          </div>
        </template>
      </section>
    </div>

    <footer v-if="chainNodes.length" class="editor-footer">
      <span
        v-if="!allDone"
        class="footer-hint linkish"
        @click="selectFirstPending"
      >还有 {{ chainNodes.length - configuredCount }} 步待配置，点击跳转</span>
      <span v-else-if="!canPublish && publishHint" class="footer-hint">{{ publishHint }}</span>
      <span v-else class="footer-hint ok">全部已配置，可以试跑或发布</span>
      <div class="footer-actions">
        <el-button type="primary" plain @click="focusQuickAdd">+ 添加</el-button>
        <el-tooltip :disabled="canTryRun" :content="tryRunHint || '请先完成配置'" placement="top">
          <span class="head-btn-wrap">
            <el-button type="success" :disabled="!canTryRun" @click="emit('try-run')">试运行</el-button>
          </span>
        </el-tooltip>
        <el-tooltip :disabled="canPublish" :content="publishHint || '请先完成配置'" placement="top">
          <span class="head-btn-wrap">
            <el-button type="warning" :disabled="!canPublish" @click="emit('publish')">发布</el-button>
          </span>
        </el-tooltip>
      </div>
    </footer>
  </main>
</template>

<script setup>
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import WorkflowParamField from './WorkflowParamField.vue'
import { bindingSummary, fieldLabel, isFieldConfigured, isValidParamKey, isMetaKey, INPUT_SOURCE_NODE, MERGE_SOURCE_NODE, normalizeFromSource } from '@/utils/workflowBinding'
import { CATEGORY_LABEL } from '@/utils/schema'

const props = defineProps({
  chainNodes: { type: Array, default: () => [] },
  selectedId: { type: String, default: '' },
  nodeName: { type: String, default: '' },
  fields: { type: Array, default: () => [] },
  bindings: { type: Array, default: () => [] },
  upstreamSources: { type: Array, default: () => [] },
  allStepSources: { type: Array, default: () => [] },
  inputFields: { type: Array, default: () => [] },
  outputSchema: { type: Object, default: () => ({ mode: 'merge', fields: [] }) },
  stepInputs: { type: Array, default: () => [] },
  stepOutputs: { type: Array, default: () => [] },
  mergePreview: { type: Object, default: () => ({}) },
  workflowStatus: { type: String, default: '' },
  workflowDirty: { type: Boolean, default: false },
  lastStepName: { type: String, default: '' },
  recentPublicOutput: { type: [Object, Array, String, Number, Boolean], default: undefined },
  components: { type: Array, default: () => [] },
  canTryRun: { type: Boolean, default: false },
  canPublish: { type: Boolean, default: false },
  tryRunHint: { type: String, default: '' },
  publishHint: { type: String, default: '' },
  canvasVisible: { type: Boolean, default: false },
})

const emit = defineEmits([
  'select', 'remove', 'move', 'add', 'update-name', 'update-bindings',
  'update-input-fields', 'update-output-schema', 'update-step-inputs', 'update-step-outputs',
  'update-step-outputs-by-node',
  'add-custom-field', 'remove-custom-field',
  'try-run', 'publish', 'toggle-canvas', 'auto-bind-upstream',
])

const router = useRouter()
const leftTab = ref('add')
const pickerKeyword = ref('')
const quickAddRef = ref(null)
const leftScrollRef = ref(null)
const showOptional = ref(false)
const showAddCustom = ref(false)
const showAddOutput = ref(false)
const showAddMergeOutput = ref('') // nodeId when adding from 出参 Tab
const pendingInsertAfterId = ref(null)
const customDraft = reactive({ key: '', description: '', required: false })
const outputDraft = reactive({ key: '', fromPath: '', description: '', nodeId: '' })
const outputDraftKeyRef = ref(null)

const selectedIndex = computed(() => {
  const idx = props.chainNodes.findIndex((item) => item.id === props.selectedId)
  return idx >= 0 ? idx : 0
})
const selectedNode = computed(() => props.chainNodes.find((item) => item.id === props.selectedId))
const requiredFields = computed(() => props.fields.filter((item) => item.required))
const optionalFields = computed(() => props.fields.filter((item) => !item.required))
const configuredCount = computed(() => props.chainNodes.filter((item) => item.configured).length)
const allDone = computed(() => props.chainNodes.length > 0 && configuredCount.value === props.chainNodes.length)
const progressPercent = computed(() => {
  if (!props.chainNodes.length) return 0
  return Math.round((configuredCount.value / props.chainNodes.length) * 100)
})
const requiredDone = computed(() =>
  requiredFields.value.filter((field) => isFieldConfigured(findBinding(field.key), field)).length,
)
const insertHintTitle = computed(() => {
  if (!pendingInsertAfterId.value) return ''
  const idx = props.chainNodes.findIndex((item) => item.id === pendingInsertAfterId.value)
  const name = props.chainNodes[idx]?.name || '当前步骤'
  return `下一步将插入到「${name}」之后（第 ${(idx >= 0 ? idx : 0) + 2} 位）`
})
const addHint = computed(() => {
  if (pendingInsertAfterId.value) return '选择接口后插入到指定位置'
  if (props.selectedId && props.chainNodes.length) return '选择接口后默认插到当前步骤之后；也可在时间线点 ⊕'
  return '从组件库挑选 HTTP 接口或数据库脚本，开始编排'
})
const nextStepLabel = computed(() => {
  const current = props.chainNodes[selectedIndex.value]
  if (current && !current.configured) return '完成本步后继续'
  const pending = props.chainNodes.find((item, i) => i > selectedIndex.value && !item.configured)
  if (pending) return '下一待配 →'
  if (selectedIndex.value >= props.chainNodes.length - 1) return '添加下一步'
  return '下一步 →'
})
const bindingCtx = computed(() => {
  const nodeNames = {}
  const nodeIndexes = {}
  props.chainNodes.forEach((item, index) => {
    nodeNames[item.id] = item.name
    nodeIndexes[item.id] = index + 1
  })
  const inputLabels = {}
  for (const item of props.inputFields || []) {
    inputLabels[item.key] = item.description || item.key
  }
  return { nodeNames, nodeIndexes, inputLabels }
})
const filteredComponents = computed(() => {
  const kw = pickerKeyword.value.trim()
  if (!kw) return props.components
  return props.components.filter((item) =>
    `${item.componentName}${item.componentCode}`.includes(kw),
  )
})
const groupedComponents = computed(() => {
  const map = new Map()
  for (const item of filteredComponents.value) {
    const key = item.category || 'HTTP'
    if (!map.has(key)) {
      map.set(key, { key, label: CATEGORY_LABEL[key] || key, items: [] })
    }
    map.get(key).items.push(item)
  }
  return [...map.values()]
})

function fieldOptionLabel(name) {
  const raw = String(name || '')
  if (raw.startsWith('rows[0].')) return `首行 · ${raw.slice('rows[0].'.length)}`
  if (raw === 'rows') return 'rows（行集）'
  if (raw === 'rowCount') return 'rowCount（行数）'
  return raw
}

const selectedResponseFields = computed(() => {
  const step = props.allStepSources.find((item) => item.id === props.selectedId)
  return step?.responseFields || []
})

const outputShapePreview = computed(() => {
  const mode = props.outputSchema?.mode || 'merge'
  if (mode === 'merge') {
    return {
      _mode: 'merge',
      ...props.mergePreview,
    }
  }
  if (mode === 'firstRow') {
    return {
      _mode: 'firstRow',
      _from: props.lastStepName
        ? `末步「${props.lastStepName}」→ rows[0]`
        : '末步查询结果 → rows[0]',
      id: '…',
      username: '…',
    }
  }
  if (mode === 'last') {
    return {
      _mode: 'last',
      _from: props.lastStepName ? `末步「${props.lastStepName}」完整结果` : '最后一步完整结果',
      rowCount: 1,
      rows: ['…'],
      truncated: false,
    }
  }
  const fields = props.outputSchema?.fields || []
  if (!fields.length) {
    return { _mode: 'fields', _note: '未配置字段；保存后按步骤合并返回', ...props.mergePreview }
  }
  const shape = {}
  for (const row of fields) {
    const key = row.key || '?'
    const path = row.fromPath ? String(row.fromPath) : '（完整）'
    shape[key] = `← ${row.fromNode || '?'}${path === '（完整）' ? path : path}`
  }
  return shape
})

const outputShapeText = computed(() => {
  try {
    return JSON.stringify(outputShapePreview.value, null, 2)
  } catch {
    return '{}'
  }
})

const recentOutputText = computed(() => {
  try {
    return JSON.stringify(props.recentPublicOutput, null, 2)
  } catch {
    return String(props.recentPublicOutput)
  }
})

watch(() => props.selectedId, async (id) => {
  showOptional.value = false
  showAddCustom.value = false
  cancelAddCustom()
  if (!id) return
  leftTab.value = 'steps'
  await nextTick()
  document.getElementById(`step-${id}`)?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
})

watch(() => props.chainNodes.length, (len, prev) => {
  if (len > (prev || 0)) leftTab.value = 'steps'
  if (!len) leftTab.value = 'add'
})

function findBinding(key) {
  return props.bindings.find((item) => item.key === key) || { key, mode: 'fixed', value: '' }
}

function patchBinding(key, patch) {
  const next = props.bindings.map((item) => (item.key === key ? { ...item, ...patch } : item))
  if (!next.find((item) => item.key === key)) {
    next.push({ key, mode: 'fixed', value: '', ...patch })
  }
  // 外部传入选了新 key 时，通知父级把入参面板补上
  if (patch.mode === 'runtime' && patch.inputKey) {
    ensureInputKey(patch.inputKey, key)
  }
  emit('update-bindings', next)
}

function ensureInputKey(inputKey, fieldKey) {
  const key = String(inputKey || '').trim()
  if (!key) return
  if (props.inputFields.some((item) => item.key === key)) return
  const field = props.fields.find((item) => item.key === fieldKey)
  emit('update-input-fields', [
    ...props.inputFields,
    {
      key,
      type: field?.type || 'string',
      required: Boolean(field?.required),
      description: field?.description || '',
    },
  ])
}

function patchInputField(index, patch) {
  const next = props.inputFields.map((item, i) => (i === index ? { ...item, ...patch } : item))
  emit('update-input-fields', next)
}

function onInputKeyChange(index, raw) {
  const nextKey = String(raw || '').trim()
  const prev = props.inputFields[index]
  if (!prev) return
  if (!isValidParamKey(nextKey)) {
    ElMessage.warning('参数键需以字母或下划线开头，仅含字母数字下划线')
    return
  }
  if (isMetaKey(nextKey)) {
    ElMessage.warning('不能使用系统保留字段名')
    return
  }
  if (props.inputFields.some((item, i) => i !== index && item.key === nextKey)) {
    ElMessage.warning('入参键已存在')
    return
  }
  const next = props.inputFields.map((item, i) => (i === index ? { ...item, key: nextKey } : item))
  emit('update-input-fields', next, { renameFrom: prev.key, renameTo: nextKey })
}

function addInputField() {
  let n = props.inputFields.length + 1
  let key = `param${n}`
  while (props.inputFields.some((item) => item.key === key)) {
    n += 1
    key = `param${n}`
  }
  emit('update-input-fields', [
    ...props.inputFields,
    { key, type: 'string', required: false, description: '' },
  ])
}

function removeInputField(index) {
  const removed = props.inputFields[index]
  const next = props.inputFields.filter((_, i) => i !== index)
  emit('update-input-fields', next, { removedKey: removed?.key })
}

function onOutputModeChange(mode) {
  emit('update-output-schema', {
    ...props.outputSchema,
    mode,
    reshapeFrom: mode === 'fields' ? 'merge' : (props.outputSchema.reshapeFrom || 'merge'),
    fields: props.outputSchema.fields || [],
  })
}

function patchOutputField(index, patch) {
  const fields = [...(props.outputSchema.fields || [])]
  fields[index] = { ...fields[index], ...patch }
  emit('update-output-schema', {
    ...props.outputSchema,
    mode: 'fields',
    reshapeFrom: 'merge',
    fields,
  })
}

function commitReshapeFieldKey(index, raw) {
  const key = String(raw || '').trim()
  const prev = props.outputSchema.fields?.[index]?.key
  if (!key) {
    ElMessage.warning('字段名不能为空')
    emit('update-output-schema', { ...props.outputSchema, fields: [...(props.outputSchema.fields || [])] })
    return
  }
  if (!isValidParamKey(key)) {
    ElMessage.warning('字段名需以字母或下划线开头，仅含字母数字下划线')
    emit('update-output-schema', { ...props.outputSchema, fields: [...(props.outputSchema.fields || [])] })
    return
  }
  if ((props.outputSchema.fields || []).some((item, i) => i !== index && item.key === key)) {
    ElMessage.warning(`字段名「${key}」已存在`)
    emit('update-output-schema', { ...props.outputSchema, fields: [...(props.outputSchema.fields || [])] })
    return
  }
  if (key === prev) return
  patchOutputField(index, { key })
}

function portsOfStep(nodeId) {
  const step = props.allStepSources.find((item) => item.id === nodeId)
  return (step?.outputPorts || []).map((item) => ({ ...item }))
}

function emitPortsForStep(nodeId, ports) {
  emit('update-step-outputs-by-node', { nodeId, outputs: ports })
}

function pathFromSelect(raw) {
  const value = String(raw || '').trim()
  if (!value || value === '*' || value === '$') return ''
  return value.startsWith('$.') ? value : `$.${value}`
}

function patchPortForStep(nodeId, index, patch) {
  const ports = portsOfStep(nodeId)
  if (!ports[index]) return
  const next = { ...ports[index], ...patch }
  if (patch.fromPath != null) {
    const prevKey = ports[index].key || ''
    const fromPath = String(patch.fromPath || '')
    if (fromPath && (/^field\d+$/i.test(prevKey) || prevKey === 'result')) {
      const leaf = fromPath.replace(/^\$\.?/, '').split('.').filter(Boolean).pop() || ''
      const suggest = String(leaf).replace(/\[\d+\]/g, '').replace(/[^a-zA-Z0-9_]/g, '_')
      if (suggest && isValidParamKey(suggest)
        && !ports.some((item, i) => i !== index && item.key === suggest)) {
        next.key = suggest
      }
    }
  }
  ports[index] = next
  emitPortsForStep(nodeId, ports)
}

function commitPortKeyForStep(nodeId, index, raw) {
  const key = String(raw || '').trim()
  const ports = portsOfStep(nodeId)
  const prev = ports[index]?.key
  if (!key) {
    ElMessage.warning('字段名不能为空')
    emitPortsForStep(nodeId, ports)
    return
  }
  if (!isValidParamKey(key)) {
    ElMessage.warning('字段名需以字母或下划线开头，仅含字母数字下划线')
    emitPortsForStep(nodeId, ports)
    return
  }
  if (ports.some((item, i) => i !== index && item.key === key)) {
    ElMessage.warning(`字段名「${key}」已存在`)
    emitPortsForStep(nodeId, ports)
    return
  }
  if (key === prev) return
  ports[index] = { ...ports[index], key }
  emitPortsForStep(nodeId, ports)
}

function removePortForStep(nodeId, index) {
  const ports = portsOfStep(nodeId)
  if (ports.length <= 1) {
    ElMessage.info('至少保留一个出参字段')
    return
  }
  emitPortsForStep(nodeId, ports.filter((_, i) => i !== index))
}

/** JSON 整形：来自按步骤合并结果 */
function reshapeSourceValue(row) {
  if (!row?.fromNode) return ''
  if (row.fromNode === MERGE_SOURCE_NODE || normalizeFromSource(row.fromSource) === 'merge') {
    const field = String(row.fromPath || '').replace(/^\$\.?/, '')
    if (!field || field === '*') return 'merge:*'
    // $.步骤名.端口 → merge:步骤名:端口
    const parts = field.split('.')
    if (parts.length >= 2) {
      return `merge:${parts[0]}:${parts.slice(1).join('.')}`
    }
    return `merge:${field}:*`
  }
  // 兼容旧 fromNode=步骤名
  const field = String(row.fromPath || '').replace(/^\$\.?/, '')
  const fieldPart = (!field || field === '*') ? '*' : field
  return `merge:${row.fromNode}:${fieldPart}`
}

function onReshapeSourceChange(index, raw) {
  let value = String(raw || '').trim()
  if (!value) return
  const row = props.outputSchema.fields?.[index] || {}
  if (value.startsWith('$.')) {
    patchOutputField(index, {
      fromNode: MERGE_SOURCE_NODE,
      fromSource: 'merge',
      fromPath: value,
      key: row.key || value.replace(/^\$\./, '').replace(/\./g, '_'),
    })
    return
  }
  if (!value.startsWith('merge:')) {
    value = `merge:${value}`
  }
  const parts = value.split(':')
  const stepName = parts[1] || ''
  const port = parts.slice(2).join(':') || '*'
  const whole = !port || port === '*'
  const fromPath = whole
    ? (stepName ? `$.${stepName}` : '')
    : `$.${stepName}.${port}`
  patchOutputField(index, {
    fromNode: MERGE_SOURCE_NODE,
    fromSource: 'merge',
    fromPath,
    key: row.key || (whole ? stepName || 'result' : port.replace(/\./g, '_')),
  })
}

function addReshapeField() {
  const first = props.allStepSources[0]
  const port = first?.outputPorts?.[0]?.key || 'result'
  const stepName = first?.name || 'step'
  const fields = [...(props.outputSchema.fields || []), {
    key: port,
    fromNode: MERGE_SOURCE_NODE,
    fromSource: 'merge',
    fromPath: `$.${stepName}.${port}`,
    description: '',
  }]
  emit('update-output-schema', {
    ...props.outputSchema,
    mode: 'fields',
    reshapeFrom: 'merge',
    fields,
  })
}

function removeOutputField(index) {
  const fields = (props.outputSchema.fields || []).filter((_, i) => i !== index)
  emit('update-output-schema', { ...props.outputSchema, mode: 'fields', reshapeFrom: 'merge', fields })
}

function stepOutputPathValue(row) {
  const path = String(row?.fromPath || '').replace(/^\$\.?/, '').trim()
  return path || '*'
}

function onStepOutputPathChange(index, raw) {
  const value = String(raw || '').trim()
  const whole = !value || value === '*' || value === '$'
  const fromPath = whole ? '' : (value.startsWith('$.') ? value : `$.${value}`)
  const patch = { fromPath }
  // 仍是默认字段名时，用路径末段自动填字段名，方便设计接口
  const prevKey = props.stepOutputs[index]?.key || ''
  if (!whole && (/^field\d+$/i.test(prevKey) || prevKey === 'result')) {
    const path = fromPath.replace(/^\$\.?/, '')
    const leaf = path.split('.').filter(Boolean).pop() || ''
    const suggest = String(leaf).replace(/\[\d+\]/g, '').replace(/[^a-zA-Z0-9_]/g, '_')
    if (suggest && isValidParamKey(suggest)
      && !props.stepOutputs.some((item, i) => i !== index && item.key === suggest)) {
      patch.key = suggest
    }
  }
  patchStepOutput(index, patch)
}

function commitStepOutputKey(index, raw) {
  const key = String(raw || '').trim()
  const prev = props.stepOutputs[index]?.key
  if (!key) {
    ElMessage.warning('字段名不能为空')
    // 触发父级重渲染以还原输入框
    emit('update-step-outputs', props.stepOutputs.map((item) => ({ ...item })))
    return
  }
  if (!isValidParamKey(key)) {
    ElMessage.warning('字段名需以字母或下划线开头，仅含字母数字下划线')
    emit('update-step-outputs', props.stepOutputs.map((item) => ({ ...item })))
    return
  }
  if (props.stepOutputs.some((item, i) => i !== index && item.key === key)) {
    ElMessage.warning(`字段名「${key}」已存在`)
    emit('update-step-outputs', props.stepOutputs.map((item) => ({ ...item })))
    return
  }
  if (key === prev) return
  patchStepOutput(index, { key })
}

function openAddOutput(nodeId = '') {
  outputDraft.key = ''
  outputDraft.fromPath = ''
  outputDraft.description = ''
  outputDraft.nodeId = nodeId || ''
  if (nodeId) {
    showAddMergeOutput.value = nodeId
    showAddOutput.value = false
  } else {
    showAddOutput.value = true
    showAddMergeOutput.value = ''
  }
  nextTick(() => {
    outputDraftKeyRef.value?.focus?.()
  })
}

function cancelAddOutput() {
  showAddOutput.value = false
  showAddMergeOutput.value = ''
  outputDraft.key = ''
  outputDraft.fromPath = ''
  outputDraft.description = ''
  outputDraft.nodeId = ''
}

function confirmAddOutput() {
  const key = String(outputDraft.key || '').trim()
  if (!key) {
    ElMessage.warning('请填写字段名')
    return
  }
  if (!isValidParamKey(key)) {
    ElMessage.warning('字段名需以字母或下划线开头，仅含字母数字下划线')
    return
  }
  const nodeId = outputDraft.nodeId || props.selectedId
  const existing = nodeId && nodeId !== props.selectedId
    ? portsOfStep(nodeId)
    : props.stepOutputs
  if (existing.some((item) => item.key === key)) {
    ElMessage.warning(`字段名「${key}」已存在`)
    return
  }
  const fromPath = pathFromSelect(outputDraft.fromPath)
  const row = {
    key,
    fromPath,
    description: String(outputDraft.description || '').trim(),
  }
  if (outputDraft.nodeId) {
    emitPortsForStep(outputDraft.nodeId, [...portsOfStep(outputDraft.nodeId), row])
  } else {
    emit('update-step-outputs', [...props.stepOutputs, row])
  }
  cancelAddOutput()
}

function patchStepOutput(index, patch) {
  const next = props.stepOutputs.map((item, i) => (i === index ? { ...item, ...patch } : item))
  emit('update-step-outputs', next)
}

function removeStepOutput(index) {
  if (props.stepOutputs.length <= 1) {
    ElMessage.info('至少保留一个出参字段')
    return
  }
  emit('update-step-outputs', props.stepOutputs.filter((_, i) => i !== index))
}

function confirmAddCustom() {
  const key = String(customDraft.key || '').trim()
  if (!isValidParamKey(key)) {
    ElMessage.warning('参数键需以字母或下划线开头，仅含字母数字下划线')
    return
  }
  if (isMetaKey(key)) {
    ElMessage.warning('不能使用系统保留字段名')
    return
  }
  if (props.fields.some((item) => item.key === key)) {
    ElMessage.warning('该参数已存在')
    return
  }
  emit('add-custom-field', {
    key,
    description: customDraft.description || key,
    required: Boolean(customDraft.required),
    type: 'string',
  })
  cancelAddCustom()
}

function cancelAddCustom() {
  showAddCustom.value = false
  customDraft.key = ''
  customDraft.description = ''
  customDraft.required = false
}

function removeCustomField(key) {
  emit('remove-custom-field', key)
}

function selectStep(id) {
  if (props.selectedId === id) return
  emit('select', id)
}

function goStep(delta) {
  if (delta > 0) {
    const current = props.chainNodes[selectedIndex.value]
    if (current && !current.configured) {
      ElMessage.warning('请先填完这一步必须要的内容')
      return
    }
    const pending = props.chainNodes.find((item, i) => i > selectedIndex.value && !item.configured)
    if (pending) {
      emit('select', pending.id)
      return
    }
    if (selectedIndex.value >= props.chainNodes.length - 1) {
      beginInsertAfter(props.selectedId, selectedIndex.value)
      return
    }
  }
  const next = props.chainNodes[selectedIndex.value + delta]
  if (next) emit('select', next.id)
}

function beginInsertAfter(afterId, index) {
  pendingInsertAfterId.value = afterId
  leftTab.value = 'add'
  nextTick(() => {
    quickAddRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    quickAddRef.value?.querySelector('input')?.focus()
  })
  const name = props.chainNodes[index]?.name || '当前步骤'
  ElMessage.info(`选择接口后，将插入到「${name}」之后`)
}

function pickComponent(item) {
  pickerKeyword.value = ''
  const afterId = pendingInsertAfterId.value || props.selectedId || null
  emit('add', item, afterId ? { afterId } : { append: !props.chainNodes.length })
  pendingInsertAfterId.value = null
  leftTab.value = 'steps'
}

function selectFirstPending() {
  leftTab.value = 'steps'
  const pending = props.chainNodes.find((item) => !item.configured)
  emit('select', (pending || props.chainNodes[0]).id)
}

function focusQuickAdd() {
  if (props.selectedId) {
    pendingInsertAfterId.value = props.selectedId
  }
  leftTab.value = 'add'
  nextTick(() => {
    quickAddRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    quickAddRef.value?.querySelector('input')?.focus()
  })
}
</script>

<style scoped>
.step-editor {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--qz-card);
  border-right: 1px solid var(--qz-border);
}
.editor-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--qz-border);
}
.head-main { flex: 1; min-width: 0; }
.head-actions { display: flex; gap: 8px; flex-shrink: 0; align-items: center; }
.head-btn-wrap { display: inline-flex; }
.editor-title { margin: 0; font-size: 16px; font-weight: 700; }
.editor-desc { margin: 4px 0 8px; font-size: 12px; color: var(--qz-text-muted); }
.progress-bar { max-width: 260px; }
.editor-body {
  flex: 1;
  display: flex;
  min-height: 0;
  overflow: hidden;
}
.left-column {
  width: 360px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--qz-border);
  background: var(--qz-fill);
  min-height: 0;
}
.left-tabs {
  display: flex;
  border-bottom: 1px solid var(--qz-border);
  flex-shrink: 0;
}
.left-tab {
  flex: 1;
  padding: 8px 2px;
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--qz-text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -1px;
}
.left-tab.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  background: var(--qz-card);
}
.left-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  overscroll-behavior: contain;
  -webkit-overflow-scrolling: touch;
}
.step-timeline {
  padding: 10px;
}
.timeline-empty {
  padding: 32px 12px;
  text-align: center;
  font-size: 13px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  align-items: center;
}
.timeline-tip {
  margin: 0 0 10px;
  padding: 0 2px;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.4;
}
.timeline-line {
  position: absolute;
  left: 22px;
  top: -10px;
  width: 2px;
  height: 10px;
  background: var(--qz-border);
  pointer-events: none;
}
.timeline-line.linked {
  background: var(--el-color-primary-light-5);
}
.timeline-deps {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 4px;
}
.timeline-deps.muted {
  font-size: 11px;
  color: var(--qz-text-muted);
}
.dep-chip {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--qz-primary-soft);
  color: var(--el-color-primary);
}
.timeline-insert {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  width: 100%;
  margin: 0 0 6px;
  padding: 4px 8px;
  border: 1px dashed var(--qz-border);
  border-radius: 8px;
  background: transparent;
  color: var(--qz-text-muted);
  font-size: 12px;
  cursor: pointer;
}
.timeline-insert:hover {
  border-color: var(--el-color-primary-light-5);
  color: var(--el-color-primary);
  background: var(--qz-primary-soft);
}
.insert-plus { font-size: 14px; line-height: 1; }
.insert-alert { margin-bottom: 10px; }
.handoff-card {
  margin: 0 0 12px;
  padding: 12px;
  border-radius: var(--qz-radius);
  background: var(--qz-primary-soft);
  border: 1px solid var(--el-color-primary-light-7);
}
.handoff-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}
.handoff-lead {
  margin: 4px 0 0;
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}
.handoff-list {
  margin: 8px 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.handoff-list li {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
  line-height: 1.45;
}
.handoff-name { font-weight: 600; }
.handoff-text { color: var(--el-color-warning-dark-2); }
.handoff-text.ok { color: var(--el-color-success-dark-2); }
.upstream-alert { margin: 0 0 12px; }
.upstream-alert-body {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 4px;
  font-size: 12px;
}
.ref-pool-summary {
  margin: 0 0 10px;
  padding: 6px 10px;
  border-radius: 6px;
  background: var(--qz-fill);
  border: 1px dashed var(--el-color-primary-light-7);
  font-size: 12px;
  color: #64748b;
  line-height: 1.5;
  white-space: pre-wrap;
}
.footer-hint.linkish {
  cursor: pointer;
  color: var(--el-color-primary);
}
.footer-hint.linkish:hover { text-decoration: underline; }
.timeline-item {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 6px;
  margin-bottom: 10px;
  border-radius: var(--qz-radius);
  border: 2px solid var(--qz-border);
  background: var(--qz-card);
  overflow: hidden;
  transition: border-color 0.12s, box-shadow 0.12s;
}
.timeline-item:hover { border-color: var(--el-color-primary-light-5); }
.timeline-item.active {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 3px var(--qz-primary-soft);
}
.timeline-item.done:not(.active) { border-color: var(--el-color-success-light-5); }
.timeline-item.pending:not(.active) { border-color: var(--el-color-warning-light-5); }
.timeline-main {
  flex: 1;
  display: flex;
  gap: 10px;
  align-items: flex-start;
  min-width: 0;
  padding: 12px 10px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
  touch-action: manipulation;
}
.timeline-main:hover { background: var(--qz-primary-soft); }
.timeline-index {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.timeline-item.done .timeline-index { background: var(--el-color-success); }
.timeline-item.pending .timeline-index { background: var(--el-color-warning); }
.timeline-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.timeline-title-row {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.timeline-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.3;
}
.timeline-sub {
  font-size: 11px;
  color: #64748b;
  word-break: break-all;
}
.timeline-summary {
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.summary-chip {
  font-size: 11px;
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-radius: 4px;
  padding: 2px 6px;
  line-height: 1.3;
}
.summary-chip.ok {
  color: var(--el-color-success-dark-2);
  background: var(--el-color-success-light-9);
}
.timeline-ops {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
  padding: 6px 6px 6px 0;
  flex-shrink: 0;
}
.op-btn {
  width: 32px;
  height: 32px;
  border: 1px solid var(--qz-border);
  border-radius: 6px;
  background: var(--qz-card);
  font-size: 14px;
  line-height: 1;
  cursor: pointer;
  color: var(--qz-text);
  touch-action: manipulation;
}
.op-btn:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
  background: var(--qz-primary-soft);
}
.op-btn.danger:hover {
  border-color: var(--el-color-danger);
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
.quick-add {
  padding: 12px;
}
.inputs-panel {
  padding: 12px;
}
.inputs-empty {
  padding: 20px 8px;
  text-align: center;
  font-size: 13px;
  color: var(--qz-text-muted);
}
.inputs-empty-sub {
  margin-top: 6px;
  font-size: 12px;
}
.inputs-empty-sub.warn {
  color: var(--el-color-warning);
}
.output-alert {
  margin-bottom: 12px;
}
.output-preview {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color-lighter);
}
.output-preview-title {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
.output-preview-title.mt {
  margin-top: 12px;
}
.output-preview-code {
  margin: 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.5;
  max-height: 180px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
}
.input-row {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
  margin-bottom: 12px;
  padding: 10px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
}
.step-output-row {
  grid-template-columns: minmax(88px, 0.9fr) minmax(120px, 1.4fr) minmax(72px, 1fr) 32px;
  align-items: center;
}
.sout-col-head {
  display: grid;
  grid-template-columns: minmax(88px, 0.9fr) minmax(120px, 1.4fr) minmax(72px, 1fr) 32px;
  gap: 8px;
  padding: 0 10px 6px;
  font-size: 11px;
  color: var(--qz-text-muted);
}
.merge-ports-block {
  margin-bottom: 14px;
  padding-bottom: 8px;
  border-bottom: 1px dashed var(--qz-border);
}
.merge-ports-block:last-of-type {
  border-bottom: none;
}
.merge-step-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  margin: 0 0 8px;
  padding: 6px 4px;
  border: none;
  background: transparent;
  cursor: pointer;
  font-size: 13px;
  font-weight: 600;
  color: var(--qz-text);
  text-align: left;
}
.merge-step-head:hover {
  color: var(--el-color-primary);
}
.merge-port-row {
  grid-template-columns: minmax(100px, 1fr) minmax(120px, 1.2fr) minmax(64px, 0.8fr) 32px;
  align-items: center;
}
.field-label-prefix {
  font-size: 11px;
  color: var(--qz-text-muted);
  white-space: nowrap;
  padding-right: 2px;
}
.step-output-row .sout-path,
.merge-port-row .sout-path {
  width: 100%;
  min-width: 0;
}
.step-output-row .op-btn:disabled,
.merge-port-row .op-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}
.section-hint.soft {
  display: block;
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--qz-text-muted);
}
@media (max-width: 1100px) {
  .sout-col-head { display: none; }
  .step-output-row,
  .merge-port-row {
    grid-template-columns: 1fr 32px;
  }
  .step-output-row .sout-key,
  .merge-port-row .sout-key { grid-column: 1; }
  .step-output-row .sout-path,
  .merge-port-row .sout-path { grid-column: 1; }
  .step-output-row .sout-desc,
  .merge-port-row .sout-desc { grid-column: 1; }
  .step-output-row .op-btn,
  .merge-port-row .op-btn { grid-column: 2; grid-row: 1; }
}
.input-key,
.input-desc { width: 100%; }
.add-input-btn { width: 100%; margin-top: 4px; }
.output-mode { margin-bottom: 12px; }
.add-param-block { margin-top: 12px; }
.add-param-form {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  border: 1px dashed var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-fill);
}
.add-param-actions { display: flex; gap: 8px; }
.quick-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--qz-text-muted);
  line-height: 1.45;
}
.quick-search { margin-bottom: 10px; }
.quick-group { margin-bottom: 14px; }
.quick-group-name {
  margin-bottom: 8px;
  font-size: 12px;
  font-weight: 600;
  color: var(--qz-text-muted);
}
.quick-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.quick-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  min-height: 52px;
  padding: 12px 14px;
  border: 1px solid var(--qz-border);
  border-radius: var(--qz-radius);
  background: var(--qz-card);
  cursor: pointer;
  text-align: left;
  touch-action: manipulation;
}
.quick-item:hover,
.quick-item:active {
  border-color: var(--el-color-primary);
  background: var(--qz-primary-soft);
}
.quick-main { flex: 1; min-width: 0; }
.quick-name { display: block; font-size: 14px; font-weight: 600; }
.quick-sub {
  display: block;
  margin-top: 2px;
  font-size: 11px;
  color: #64748b;
}
.quick-add-btn {
  flex-shrink: 0;
  padding: 4px 12px;
  border-radius: 999px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
}
.quick-empty {
  padding: 24px;
  text-align: center;
  font-size: 13px;
  color: var(--qz-text-muted);
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: center;
}
.step-config {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding: 16px 20px 72px;
}
.config-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 240px;
  color: var(--qz-text-muted);
  font-size: 14px;
}
.config-head { margin-bottom: 14px; }
.config-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 600;
  color: var(--el-color-primary);
  background: var(--qz-primary-soft);
  padding: 2px 8px;
  border-radius: 999px;
  margin-bottom: 6px;
}
.config-title { margin: 0; font-size: 18px; font-weight: 700; }
.config-sub { margin: 4px 0 0; font-size: 12px; color: #64748b; }
.step-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 0 14px;
  border-bottom: 1px solid var(--qz-border);
  margin-bottom: 14px;
}
.config-form { margin-bottom: 8px; }
.config-empty { padding: 12px 0; }
.empty-actions { display: flex; gap: 8px; margin-top: 14px; flex-wrap: wrap; }
.field-section { margin-bottom: 16px; }
.section-head,
.section-toggle {
  display: flex;
  justify-content: space-between;
  align-items: center;
  width: 100%;
  margin-bottom: 10px;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
}
.section-toggle:hover .section-title {
  color: var(--el-color-primary);
}
.section-title { font-size: 13px; font-weight: 700; }
.section-hint { font-size: 12px; color: var(--qz-text-muted); }
.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-top: 1px solid var(--qz-border);
  background: var(--qz-card);
  flex-shrink: 0;
  z-index: 5;
}
.footer-hint { font-size: 13px; color: var(--el-color-warning); }
.footer-hint.ok { color: var(--el-color-success); }
.footer-actions { display: flex; gap: 8px; flex-wrap: wrap; }
@media (max-width: 900px) {
  .editor-body { flex-direction: column; }
  .left-column { width: 100%; max-height: 42vh; }
}
</style>
