const form = document.getElementById("bookingForm");
const message = document.getElementById("message");
const list = document.getElementById("appointmentList");
const count = document.getElementById("count");
const dateInput = document.getElementById("date");

function formatDay(iso) {
    // Parse as local time so the date doesn't shift by timezone
    const d = new Date(iso + "T00:00:00");
    return d.toLocaleDateString(undefined, {
        weekday: "long", day: "numeric", month: "long", year: "numeric"
    });
}

function showMessage(text, isError) {
    message.textContent = text;
    message.className = isError ? "error" : "success";
}

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text; // textContent avoids HTML injection
    return node;
}

const EMPTY_ART = '<svg viewBox="0 0 120 100" width="120" height="100" aria-hidden="true">' +
    '<rect x="16" y="20" width="88" height="70" rx="8" fill="#fff" stroke="#0f5c73" stroke-width="3"/>' +
    '<path d="M16 40h88" stroke="#0f5c73" stroke-width="3"/>' +
    '<path d="M38 12v16M82 12v16" stroke="#0f5c73" stroke-width="4" stroke-linecap="round"/>' +
    '<path d="M60 54v22M49 65h22" stroke="#0f5c73" stroke-width="5" stroke-linecap="round"/></svg>';

let highlightKey = null;

function render(appointments) {
    list.innerHTML = "";
    count.textContent = appointments.length === 1
        ? "1 appointment"
        : appointments.length + " appointments";

    if (appointments.length === 0) {
        const empty = el("div", "empty");
        empty.innerHTML = EMPTY_ART;
        empty.appendChild(el("p", "", "No appointments yet. Use the form to book the first one."));
        list.appendChild(empty);
        return;
    }

    appointments.sort((a, b) => (a.date + a.time).localeCompare(b.date + b.time));

    let currentDate = null;
    for (const a of appointments) {
        if (a.date !== currentDate) {
            currentDate = a.date;
            list.appendChild(el("h3", "day", formatDay(a.date)));
        }

        const slot = el("div", "slot");
        if (a.date + " " + a.time === highlightKey) slot.classList.add("new");
        const time = el("time", "", a.time);
        const info = el("div");
        info.appendChild(el("div", "who", a.name));
        info.appendChild(el("div", "why", a.reason));
        info.appendChild(el("div", "meta", "Patient ID " + a.id + ", age " + a.age));

        slot.appendChild(time);
        slot.appendChild(info);
        list.appendChild(slot);
    }
    highlightKey = null;
}

async function loadAppointments() {
    try {
        const response = await fetch("/api/appointments");
        render(await response.json());
    } catch (err) {
        showMessage("Could not load appointments. Check that the server is running.", true);
    }
}

form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = new URLSearchParams({
        name: document.getElementById("name").value,
        id: document.getElementById("id").value,
        age: document.getElementById("age").value,
        date: dateInput.value,
        time: document.getElementById("time").value,
        reason: document.getElementById("reason").value
    });

    try {
        const response = await fetch("/api/appointments", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: data
        });
        const result = await response.json();

        if (response.ok) {
            highlightKey = data.get("date") + " " + data.get("time");
            showMessage("Appointment booked.", false);
            form.reset();
            loadAppointments();
        } else {
            showMessage(result.error, true);
        }
    } catch (err) {
        showMessage("Could not reach the server.", true);
    }
});

const today = new Date();
document.getElementById("today").textContent = today.toLocaleDateString(undefined, {
    weekday: "long", day: "numeric", month: "long", year: "numeric"
});
dateInput.min = today.getFullYear() + "-" +
    String(today.getMonth() + 1).padStart(2, "0") + "-" +
    String(today.getDate()).padStart(2, "0");

loadAppointments();
