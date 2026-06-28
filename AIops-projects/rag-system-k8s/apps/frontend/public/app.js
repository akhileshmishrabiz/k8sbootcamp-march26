const API_BASE = window.location.origin;

const uploadForm = document.getElementById("upload-form");
const uploadStatus = document.getElementById("upload-status");
const documentList = document.getElementById("document-list");
const chatForm = document.getElementById("chat-form");
const chatLog = document.getElementById("chat-log");
const chatStatus = document.getElementById("chat-status");
const docFile = document.getElementById("doc-file");
const docContent = document.getElementById("doc-content");
const docTitle = document.getElementById("doc-title");
const questionInput = document.getElementById("question");
const chatSubmit = document.getElementById("chat-submit");

let llmReady = false;

function setChatReady(ready, message) {
  llmReady = ready;
  questionInput.disabled = !ready;
  chatSubmit.disabled = !ready;
  chatStatus.textContent = message;
  chatStatus.className = ready ? "status ready" : "status waiting";
}

async function checkHealth() {
  const response = await fetch(`${API_BASE}/health`);
  const contentType = response.headers.get("content-type") || "";
  if (!contentType.includes("application/json")) {
    throw new Error("Health endpoint returned HTML — port-forward api-gateway, not rag-frontend alone");
  }
  if (!response.ok) {
    throw new Error("Health check failed");
  }
  const data = await response.json();
  if (data.ollama) {
    setChatReady(true, "LLM ready. Chat works even without uploaded documents.");
    return true;
  }
  setChatReady(false, "LLM not ready yet. Waiting for Ollama models...");
  return false;
}

async function fetchDocuments() {
  const response = await fetch(`${API_BASE}/api/documents`);
  if (!response.ok) {
    throw new Error("Documents API unavailable");
  }
  const data = await response.json();
  documentList.innerHTML = "";

  if (!data.documents.length) {
    documentList.innerHTML = "<li>No documents indexed yet. You can still chat with the LLM.</li>";
    return;
  }

  data.documents.forEach((doc) => {
    const item = document.createElement("li");
    item.textContent = doc.title;
    documentList.appendChild(item);
  });
}

function appendMessage(role, text, sources = []) {
  const wrapper = document.createElement("div");
  wrapper.className = `message ${role}`;
  wrapper.innerHTML = `<strong>${role === "user" ? "You" : "Assistant"}</strong><p>${text}</p>`;

  if (sources.length) {
    const sourceBlock = document.createElement("div");
    sourceBlock.className = "sources";
    sourceBlock.innerHTML = `<strong>Sources</strong><ul>${sources
      .map(
        (source) =>
          `<li>${source.doc_title} (chunk ${source.chunk_index}, score ${source.score.toFixed(3)})</li>`
      )
      .join("")}</ul>`;
    wrapper.appendChild(sourceBlock);
  }

  chatLog.appendChild(wrapper);
  chatLog.scrollTop = chatLog.scrollHeight;
}

async function pollUntilReady() {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    try {
      if (await checkHealth()) {
        return;
      }
    } catch (error) {
      const hint =
        error.message && error.message.includes("port-forward")
          ? error.message
          : "API not reachable yet. Waiting for pods to become ready...";
      setChatReady(false, hint);
    }
    await new Promise((resolve) => setTimeout(resolve, 5000));
  }
}

docFile.addEventListener("change", async (event) => {
  const file = event.target.files[0];
  if (!file) return;
  const text = await file.text();
  docContent.value = text;
  if (!docTitle.value) {
    docTitle.value = file.name;
  }
});

uploadForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  uploadStatus.textContent = "Ingesting document...";

  const response = await fetch(`${API_BASE}/api/documents`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      title: docTitle.value.trim(),
      content: docContent.value.trim(),
    }),
  });

  const data = await response.json();
  if (!response.ok) {
    uploadStatus.textContent = data.detail || "Upload failed";
    return;
  }

  uploadStatus.textContent = `Indexed ${data.chunk_count} chunks for "${data.title}"`;
  docContent.value = "";
  docFile.value = "";
  await fetchDocuments();
});

chatForm.addEventListener("submit", async (event) => {
  event.preventDefault();
  const question = questionInput.value.trim();
  if (!question || !llmReady) return;

  appendMessage("user", question);
  questionInput.value = "";
  questionInput.disabled = true;
  chatSubmit.disabled = true;
  chatStatus.textContent = "Thinking... (first response can take up to a minute on CPU)";

  try {
    const response = await fetch(`${API_BASE}/api/chat`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question }),
    });
    const data = await response.json();
    if (!response.ok) {
      appendMessage("assistant", data.detail || "Chat request failed");
      return;
    }
    appendMessage("assistant", data.answer, data.sources || []);
  } finally {
    if (llmReady) {
      questionInput.disabled = false;
      chatSubmit.disabled = false;
      chatStatus.textContent = "LLM ready. Chat works even without uploaded documents.";
      questionInput.focus();
    }
  }
});

pollUntilReady()
  .then(() => fetchDocuments())
  .catch(() => {
    documentList.innerHTML = "<li>Could not load document list yet.</li>";
  });
