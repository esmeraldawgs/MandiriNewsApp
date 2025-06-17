package com.example.mandirinewsapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit

@Composable
fun MandiriNewsAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MandiriNewsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val viewModel: NewsViewModel = viewModel()
                    NewsApp(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsApp(viewModel: NewsViewModel) {
    val scrollState = rememberLazyListState()
    var rotationAngle by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_mandiri_logo),
                            contentDescription = "Mandiri Logo",
                            modifier = Modifier
                                .size(36.dp)
                                .padding(end = 8.dp)
                        )
                        Text("Mandiri News", style = MaterialTheme.typography.titleLarge)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF003a70), // Changed to blue #003a70
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    rotationAngle += 360f
                    coroutineScope.launch {
                        try {
                            scrollState.animateScrollToItem(0)
                            viewModel.refreshData()
                        } catch (e: Exception) {
                            viewModel.setError("Refresh error: ${e.localizedMessage}")
                        }
                    }
                },
                containerColor = Color(0xFF003a70),
                contentColor = Color.White
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    modifier = Modifier.rotate(rotationAngle)
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // Search Bar
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Text(
                text = "Berita Terbaru",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
            )
            NewsScreen(viewModel, scrollState, searchQuery)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = Color.Gray
            )
        },
        colors = TextFieldDefaults.textFieldColors(
            containerColor = Color.White,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = Color(0xFF003a70),
            focusedTextColor = Color.Black,
            unfocusedTextColor = Color.Black
        ),
        placeholder = {
            Text("Cari berita...", color = Color.Gray)
        },
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        singleLine = true
    )
}

@Composable
fun NewsScreen(viewModel: NewsViewModel, scrollState: LazyListState, searchQuery: String) {
    val state = viewModel.state.collectAsState().value
    val filteredArticles = remember(state.articles, searchQuery) {
        if (searchQuery.isEmpty()) {
            state.articles
        } else {
            state.articles.filter { article ->
                article.title?.contains(searchQuery, ignoreCase = true) == true ||
                        article.description?.contains(searchQuery, ignoreCase = true) == true ||
                        article.content?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.getNews()
    }

    when {
        state.isLoading && state.articles.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error != null -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
                Button(
                    onClick = { viewModel.refreshData() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Coba Lagi")
                }
            }
        }
        filteredArticles.isEmpty() -> {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (searchQuery.isEmpty()) "Tidak ada berita yang tersedia"
                    else "Tidak ditemukan hasil untuk '$searchQuery'",
                    modifier = Modifier.padding(16.dp)
                )
                Button(
                    onClick = { viewModel.refreshData() },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Muat Ulang")
                }
            }
        }
        else -> {
            NewsList(
                articles = filteredArticles,
                isLoadingMore = state.isLoadingMore,
                scrollState = scrollState
            )
        }
    }
}

@Composable
fun NewsList(
    articles: List<Article>,
    isLoadingMore: Boolean,
    scrollState: LazyListState
) {
    LazyColumn(
        state = scrollState,
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(articles) { article ->
            NewsCard(article)
        }

        if (isLoadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun NewsCard(article: Article) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            article.urlToImage?.takeIf { it.isNotBlank() }?.let { url ->
                Image(
                    painter = rememberAsyncImagePainter(url),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Text(
                text = article.title ?: "Tidak ada judul",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = article.description ?: "Tidak ada deskripsi",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

class NewsViewModel : ViewModel() {
    private val _state = MutableStateFlow(NewsState())
    val state = _state.asStateFlow()

    init {
        getNews()
    }

    fun setError(message: String) {
        _state.value = _state.value.copy(
            error = message,
            isLoading = false
        )
    }

    fun getNews() {
        _state.value = _state.value.copy(
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            try {
                val response = try {
                    RetrofitInstance.api.getTopHeadlines("techcrunch")
                } catch (e: Exception) {
                    setError("Connection error: ${e.localizedMessage}")
                    return@launch
                }

                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        articles = response.body()?.articles ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    setError("Server error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                setError("Unexpected error: ${e.localizedMessage}")
            }
        }
    }

    fun refreshData() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(isLoading = true, error = null)
                val response = try {
                    RetrofitInstance.api.getTopHeadlines("techcrunch")
                } catch (e: Exception) {
                    setError("Connection error: ${e.localizedMessage}")
                    return@launch
                }

                if (response.isSuccessful) {
                    _state.value = _state.value.copy(
                        articles = response.body()?.articles ?: emptyList(),
                        isLoading = false
                    )
                } else {
                    setError("Server error: ${response.code()} - ${response.message()}")
                }
            } catch (e: Exception) {
                setError("Unexpected error: ${e.localizedMessage}")
            }
        }
    }
}

data class NewsState(
    val articles: List<Article> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null
)

object RetrofitInstance {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: NewsApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://newsapi.org/v2/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(NewsApi::class.java)
    }
}

interface NewsApi {
    @GET("top-headlines")
    suspend fun getTopHeadlines(
        @Query("sources") sources: String,
        @Query("apiKey") apiKey: String = API_KEY
    ): Response<NewsResponse>
}

const val API_KEY = "b85c30c914bd4ac383798fa8cc1a804d"

data class NewsResponse(
    val status: String,
    val totalResults: Int,
    val articles: List<Article>
)

data class Article(
    val source: Source?,
    val author: String?,
    val title: String?,
    val description: String?,
    val url: String?,
    val urlToImage: String?,
    val publishedAt: String?,
    val content: String?
)

data class Source(
    val id: String?,
    val name: String?
)
//Author : Esmeralda Wangsa