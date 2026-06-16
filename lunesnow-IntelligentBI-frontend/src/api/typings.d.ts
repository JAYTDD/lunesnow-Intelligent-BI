declare namespace API {
  type BaseResponseBiResponse = {
    code?: number
    data?: BiResponse
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseChartStatisticsVO = {
    code?: number
    data?: ChartStatisticsVO
    message?: string
  }

  type BaseResponseChartVO = {
    code?: number
    data?: ChartVO
    message?: string
  }

  type BaseResponseListMapStringString = {
    code?: number
    data?: Record<string, any>[]
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseMapStringObject = {
    code?: number
    data?: Record<string, any>
    message?: string
  }

  type BaseResponsePageChart = {
    code?: number
    data?: PageChart
    message?: string
  }

  type BaseResponsePageChartVO = {
    code?: number
    data?: PageChartVO
    message?: string
  }

  type BaseResponsePageUser = {
    code?: number
    data?: PageUser
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type BiResponse = {
    genChart?: string
    genResult?: string
    chartId?: number
    status?: string
    execMessage?: string
  }

  type Chart = {
    id?: number
    name?: string
    goal?: string
    chartType?: string
    genChart?: string
    genResult?: string
    status?: string
    execMessage?: string
    waitTime?: number
    runningTime?: number
    chartData?: string
    userId?: number
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ChartAddRequest = {
    goal?: string
    name?: string
    chartData?: string
    chartType?: string
  }

  type ChartEditConfigRequest = {
    id?: number
    genChart?: string
  }

  type ChartEditRequest = {
    id?: number
    name?: string
    goal?: string
    chartData?: string
    chartType?: string
  }

  type ChartQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    name?: string
    goal?: string
    chartType?: string
    userId?: number
  }

  type ChartStatisticsVO = {
    totalCount?: number
    successCount?: number
    failedCount?: number
    runningCount?: number
    successRate?: number
    recentCharts?: ChartVO[]
  }

  type ChartUpdateRequest = {
    id?: number
    name?: string
    goal?: string
    chartData?: string
    chartType?: string
  }

  type ChartVO = {
    id?: number
    name?: string
    goal?: string
    chartType?: string
    genChart?: string
    genResult?: string
    status?: string
    execMessage?: string
    userId?: number
    user?: UserVO
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type DeleteRequest = {
    id?: number
  }

  type getChartByAIParams = {
    name: string
    chartType: string
    goal: string
  }

  type getChartDataParams = {
    chartId: number
  }

  type getChartStatusParams = {
    id: number
  }

  type getChartVOByIdParams = {
    id: number
  }

  type getRateLimitStatusParams = {
    key: string
  }

  type getUserByIdParams = {
    id: number
  }

  type getUserVOByIdParams = {
    id: number
  }

  type LoginUserVO = {
    id?: number
    userName?: string
    userAvatar?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type OrderItem = {
    column?: string
    asc?: boolean
  }

  type PageChart = {
    records?: Chart[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageChart
    searchCount?: PageChart
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageChartVO = {
    records?: ChartVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageChartVO
    searchCount?: PageChartVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageUser = {
    records?: User[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageUser
    searchCount?: PageUser
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type PageUserVO = {
    records?: UserVO[]
    total?: number
    size?: number
    current?: number
    orders?: OrderItem[]
    optimizeCountSql?: PageUserVO
    searchCount?: PageUserVO
    optimizeJoinOfCountSql?: boolean
    maxLimit?: number
    countId?: string
    pages?: number
  }

  type resetRateLimitParams = {
    key: string
  }

  type retryChartGenParams = {
    id: number
  }

  type uploadFileParams = {
    uploadFileRequest: UploadFileRequest
  }

  type UploadFileRequest = {
    biz?: string
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userRole?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    current?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateMyRequest = {
    userName?: string
    userAvatar?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userName?: string
    userAvatar?: string
    userRole?: string
    createTime?: string
  }
}
