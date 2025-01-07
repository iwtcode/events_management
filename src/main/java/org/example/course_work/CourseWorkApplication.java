package org.example.course_work;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import jakarta.persistence.*;
import jakarta.servlet.http.HttpSession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
class CourseWorkApplication {
	public static void main(String[] args) {
		SpringApplication.run(CourseWorkApplication.class, args);
	}
}

@Controller
class AuthController {

	@Autowired
	private LoginRepository loginRepository;

	@GetMapping("/")
	public String redirectToLogin() {
		return "redirect:/login";
	}

	@GetMapping("/login")
	public String loginPage() {
		return "login";
	}

	@PostMapping("/login")
	public String login(@RequestParam String user, @RequestParam String password, Model model, HttpSession session) {
		var optionalLogin = loginRepository.findByUserAndPassword(user, password);

		if (optionalLogin.isPresent()) {
			Login loggedInUser = optionalLogin.get();
			session.setAttribute("user", loggedInUser.getUser());
			session.setAttribute("role", loggedInUser.getRole());

			if ("manager".equals(loggedInUser.getRole())) {
				return "redirect:/manager";
			}
			if ("admin".equals(loggedInUser.getRole())) {
				return "redirect:/admin";
			}
			else return "redirect:/events";
		} else {
			model.addAttribute("error", "Неправильный логин или пароль.");
			return "login";
		}
	}

	@GetMapping("/logout")
	public String logout(HttpSession session) {
		session.invalidate();
		return "redirect:/login";
	}

	@GetMapping("/register")
	public String registerPage() {
		return "register";
	}

	@PostMapping("/register")
	public String register(@RequestParam String user, @RequestParam String password, Model model, HttpSession session) {
		Optional<Login> existingUser = loginRepository.findByUser(user);

		if (existingUser.isPresent()) {
			model.addAttribute("error", "Логин уже занят.");
			return "register";
		}

		Login newUser = new Login();
		newUser.setUser(user);
		newUser.setPassword(password);
		newUser.setRole("user");

		loginRepository.save(newUser);

		session.setAttribute("user", user);
		session.setAttribute("role", newUser.getRole());
		return "redirect:/events";
	}
}

@Controller
class EventController {

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private LoginRepository loginRepository;

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private IdeaRepository ideaRepository;

	@GetMapping("/events")
	public String eventsPage(HttpSession session, Model model) {
		String user = (String) session.getAttribute("user");
		if (user == null) {
			return "redirect:/login";
		}

		var username = loginRepository.findByUser(user).orElse(null);
		if (username == null) {
			return "redirect:/login";
		}

		List<Event> allEvents = eventRepository.findAll();
		List<Team> userTeams = teamRepository.findByUserId(username.getId());
		List<Integer> userEventIds = userTeams.stream()
				.map(Team::getEventId)
				.toList();
		List<Event> userEvents = allEvents.stream()
				.filter(event -> userEventIds.contains(event.getId()))
				.toList();

		List<Event> availableEvents = allEvents.stream()
				.filter(event -> !userEventIds.contains(event.getId()))
				.filter(event -> "Open".equals(event.getStatus()))
				.toList();

		Map<Integer, String> eventParticipants = new HashMap<>();
		for (Team userTeam : userTeams) {
			List<Team> teamMembers = teamRepository.findByTeamId(userTeam.getTeamId())
					.stream()
					.filter(team -> team.getEventId() == userTeam.getEventId()) // Ensure event ID matches
					.toList();

			String participantLogins = teamMembers.stream()
					.map(team -> loginRepository.findById(team.getUserId())
							.map(Login::getUser)
							.orElse("Unknown"))
					.sorted()
					.collect(Collectors.joining(", "));

			eventParticipants.put(userTeam.getEventId(), participantLogins);
		}

		Map<Integer, String> eventImages = new HashMap<>();
		for (Event event : allEvents) {
			if (event.getImage() != null) {
				String base64Image = Base64.getEncoder().encodeToString(event.getImage());
				eventImages.put(event.getId(), base64Image);
			}
		}

		model.addAttribute("availableEvents", availableEvents);
		model.addAttribute("userEvents", userEvents);
		model.addAttribute("eventParticipants", eventParticipants);
		model.addAttribute("eventImages", eventImages);
		return "events";
	}

	@PostMapping("/events/apply")
	public String applyForEvent(@RequestParam int eventId, HttpSession session, Model model) {
		String user = (String) session.getAttribute("user");
		if (user == null) {
			return "redirect:/login";
		}

		var optionalUser = loginRepository.findByUser(user);
		if (optionalUser.isEmpty()) {
			return "redirect:/login";
		}

		int userId = optionalUser.get().getId();

		var existingApplication = applicationRepository.findByUserIdAndEventId(userId, eventId);
		if (existingApplication.isEmpty()) {
			Application application = new Application();
			application.setUserId(userId);
			application.setEventId(eventId);
			applicationRepository.save(application);
			model.addAttribute("successEventId", eventId);
		} else {
			model.addAttribute("errorEventId", eventId);
		}

		return eventsPage(session, model);
	}

	@PostMapping("/events/submitIdea")
	public String submitIdea(@RequestParam String title, @RequestParam String description, HttpSession session, Model model) {
		String user = (String) session.getAttribute("user");
		if (user == null) {
			return "redirect:/login";
		}

		var optionalUser = loginRepository.findByUser(user);
		if (optionalUser.isEmpty()) {
			return "redirect:/login";
		}

		Idea idea = new Idea();
		idea.setUserId(optionalUser.get().getId());
		idea.setTitle(title);
		idea.setDescription(description);

		ideaRepository.save(idea);
		model.addAttribute("message", "Idea submitted successfully!");
		return "redirect:/events";
	}
}

@Controller
class ManagerController {

	@Autowired
	private ApplicationRepository applicationRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private LoginRepository loginRepository;

	@Autowired
	private EventRepository eventRepository;

	@GetMapping("/manager")
	public String managerPage(HttpSession session, Model model) {
		String user = (String) session.getAttribute("user");
		String role = (String) session.getAttribute("role");

		if (user == null) {
			return "redirect:/login";
		} else if (!"manager".equals(role)) {
			return "redirect:/events";
		}

		var apps = applicationRepository.findAll();
		var userMap = loginRepository.findAll().stream()
				.collect(Collectors.toMap(Login::getId, Login::getUser));
		var eventMap = eventRepository.findAll().stream()
				.collect(Collectors.toMap(Event::getId, Event::getTitle));

		var events = eventRepository.findAll();

		model.addAttribute("apps", apps);
		model.addAttribute("userMap", userMap);
		model.addAttribute("eventMap", eventMap);
		model.addAttribute("events", events);
		return "manager";
	}

	@PostMapping("/manager/accept")
	public String acceptApplication(
			@RequestParam int appId,
			@RequestParam int teamId,
			HttpSession session,
			Model model
	) {

		Optional<Application> optionalApp = applicationRepository.findById(appId);
		if (optionalApp.isPresent()) {
			Application app = optionalApp.get();

			Team team = new Team();
			team.setUserId(app.getUserId());
			team.setEventId(app.getEventId());
			team.setTeamId(teamId);

			teamRepository.save(team);
			applicationRepository.delete(app);

			model.addAttribute("successMessage", "Application accepted and added to team successfully!");
		} else {
			model.addAttribute("errorMessage", "Application not found.");
		}

		return managerPage(session, model);
	}

	@PostMapping("/manager/reject")
	public String rejectApplication(@RequestParam int appId, HttpSession session, Model model) {
		if (applicationRepository.existsById(appId)) {
			applicationRepository.deleteById(appId);
			model.addAttribute("successMessage", "Application rejected successfully.");
		} else {
			model.addAttribute("errorMessage", "Application not found.");
		}

		return managerPage(session, model);
	}

	@PostMapping("/manager/changeStatus")
	public String changeEventStatus(@RequestParam int eventId, @RequestParam String newStatus, HttpSession session, Model model) {
		Optional<Event> optionalEvent = eventRepository.findById(eventId);
		if (optionalEvent.isPresent()) {
			Event event = optionalEvent.get();
			event.setStatus(newStatus);
			eventRepository.save(event);

			model.addAttribute("eventSuccessMessage", "Event status updated successfully!");
		} else {
			model.addAttribute("eventErrorMessage", "Event not found.");
		}

		return managerPage(session, model);
	}

	@PostMapping("/manager/addComment")
	public String addComment(
			@RequestParam int eventId,
			@RequestParam String comment,
			HttpSession session,
			Model model
	) {
		String username = (String) session.getAttribute("user");

		Optional<Event> optionalEvent = eventRepository.findById(eventId);
		if (optionalEvent.isPresent()) {
			Event event = optionalEvent.get();
			String existingComment = event.getComment();
			if (existingComment == null || existingComment.isBlank()) {
				event.setComment(username + ": " + comment);
			} else {
				event.setComment(existingComment + "\\n" + username + ": " + comment);
			}
			eventRepository.save(event);

			model.addAttribute("eventSuccessMessage", "Comment added successfully!");
		} else {
			model.addAttribute("eventErrorMessage", "Event not found.");
		}

		return managerPage(session, model);
	}
}

@Controller
class AdminController {

	@Autowired
	private EventRepository eventRepository;

	@Autowired
	private IdeaRepository ideaRepository;

	@Autowired
	private LoginRepository loginRepository;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private ImageGenerator imageGenerator;

	@GetMapping("/admin")
	public String adminPage(HttpSession session, Model model) {
		String user = (String) session.getAttribute("user");
		String role = (String) session.getAttribute("role");

		if (user == null) {
			return "redirect:/login";
		} else if (!"admin".equals(role)) {
			return "redirect:/events";
		}

		List<Idea> ideas = ideaRepository.findAll();
		List<Login> logins = loginRepository.findAll()
				.stream()
				.filter(u -> !"admin".equals(u.getRole()))
				.collect(Collectors.toList());

		Map<Integer, List<Event>> userEventsMap = new HashMap<>();
		for (Login login : logins) {
			List<Integer> eventIds = teamRepository.findByUserId(login.getId())
					.stream()
					.map(Team::getEventId)
					.collect(Collectors.toList());
			List<Event> userEvents = eventRepository.findAllById(eventIds);
			userEventsMap.put(login.getId(), userEvents);
		}

		if (ideas.isEmpty()) {
			model.addAttribute("ideaError", "No ideas available.");
		} else {
			model.addAttribute("ideas", ideas);
		}

		if (logins.isEmpty()) {
			model.addAttribute("loginError", "No logins available.");
		} else {
			model.addAttribute("logins", logins);
		}

		model.addAttribute("userEventsMap", userEventsMap);
		return "admin";
	}

	@PostMapping("/admin/createEvent")
	public String createEvent(
			@RequestParam String title,
			@RequestParam String description,
			HttpSession session,
			Model model) {
		Optional<Event> existingEvent = eventRepository.findByTitle(title);

        if (existingEvent.isPresent()) {
			model.addAttribute("createError", "An event with this title already exists.");
		} else {
			Event event = new Event();
			event.setTitle(title);
			event.setDescription(description);
			event.setStatus("Open");

			try {
				String prompt = title + "\n" + description;
				byte[] imageData = imageGenerator.generateImage(prompt);

				if (imageData != null) {
					event.setImage(imageData);
				}
			} catch (IOException | InterruptedException e) {
				System.err.println("An error occurred: " + e.getMessage());
			}

			eventRepository.save(event);
			model.addAttribute("createMessage", "Event created successfully!");
		}

		return adminPage(session, model);
	}

	@PostMapping("/admin/deleteEvent")
	public String deleteEvent(@RequestParam String eventTitle, HttpSession session, Model model) {
		Optional<Event> optionalEvent = eventRepository.findByTitle(eventTitle);

		if (optionalEvent.isEmpty()) {
			model.addAttribute("deleteError", "An event with this title does not exist.");
		} else {
			eventRepository.delete(optionalEvent.get());
			model.addAttribute("deleteMessage", "Event deleted successfully!");
		}

		return adminPage(session, model);
	}

	@PostMapping("/admin/selectIdea")
	public String selectIdea(@RequestParam int ideaId, HttpSession session, Model model) {
		Optional<Idea> optionalIdea = ideaRepository.findById(ideaId);

		if (optionalIdea.isEmpty()) {
			model.addAttribute("errorMessage", "Idea not found.");
		} else {
			Idea idea = optionalIdea.get();
			Event event = new Event();
			event.setTitle(idea.getTitle());
			event.setDescription(idea.getDescription());
			event.setStatus("Open");

			try {
				String prompt = idea.getTitle() + "\n" + idea.getDescription();
				byte[] imageData = imageGenerator.generateImage(prompt);

				if (imageData != null) {
					event.setImage(imageData);
				}
			} catch (IOException | InterruptedException e) {
				System.err.println("An error occurred: " + e.getMessage());
			}

			eventRepository.save(event);
			ideaRepository.delete(idea);
			model.addAttribute("successMessage", "Idea successfully selected and converted to event.");
		}

		return adminPage(session, model); // Обновляем данные
	}

	@PostMapping("/admin/rejectIdea")
	public String rejectIdea(@RequestParam int ideaId, HttpSession session, Model model) {
		Optional<Idea> optionalIdea = ideaRepository.findById(ideaId);

		if (optionalIdea.isEmpty()) {
			model.addAttribute("errorMessage", "Idea not found.");
		} else {
			ideaRepository.delete(optionalIdea.get());
			model.addAttribute("successMessage", "Idea successfully rejected.");
		}

		return adminPage(session, model); // Обновляем данные
	}

	@PostMapping("/admin/updateUserRole")
	public String updateUserRole(
			@RequestParam int userId,
			@RequestParam String role,
			HttpSession session,
			Model model) {
		Optional<Login> optionalUser = loginRepository.findById(userId);

		if (optionalUser.isEmpty()) {
			model.addAttribute("userError", "User not found.");
		} else {
			Login user = optionalUser.get();
			user.setRole(role);
			loginRepository.save(user);
			model.addAttribute("userMessage", "Role updated successfully!");
		}

		return adminPage(session, model); // Обновляем данные
	}

	@PostMapping("/admin/removeUserFromEvent")
	public String removeUserFromEvent(
			@RequestParam int userId,
			@RequestParam int eventId,
			HttpSession session,
			Model model) {
		Optional<Login> optionalUser = loginRepository.findById(userId);
		Optional<Event> optionalEvent = eventRepository.findById(eventId);

		if (optionalUser.isEmpty()) {
			model.addAttribute("userError", "User not found.");
		} else if (optionalEvent.isEmpty()) {
			model.addAttribute("eventError", "Event not found.");
		} else {
			List<Team> userTeams = teamRepository.findByUserId(userId).stream()
					.filter(team -> team.getEventId() == eventId)
					.toList();

			if (userTeams.isEmpty()) {
				model.addAttribute("userError", "User is not part of the selected event.");
			} else {
				teamRepository.deleteAll(userTeams);
				model.addAttribute("userMessage", "User removed from the event successfully!");
			}
		}

		return adminPage(session, model); // Обновляем данные
	}
}

@Component
class ImageGenerator {
	private final String apiUrl;
	private final String apiKey;

	@Autowired
	public ImageGenerator(@Value("${API_URL}") String apiUrl, @Value("${API_KEY}") String apiKey) {
		this.apiUrl = apiUrl;
		this.apiKey = apiKey;
	}

	public byte[] generateImage(String prompt) throws IOException, InterruptedException {
		String payload = "{\"inputs\":\"" + prompt + "\"}";

		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(apiUrl))
				.header("Authorization", "Bearer " + apiKey)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(payload))
				.build();
		HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

		if (response.statusCode() != 200) {
			System.err.println("Failed to generate image: HTTP " + response.statusCode());
			return null;
		}
		return response.body();
	}
}

@Repository
interface LoginRepository extends JpaRepository<Login, Integer> {
	Optional<Login> findByUser(String user);
	Optional<Login> findByUserAndPassword(String user, String password);
}

@Repository
interface EventRepository extends JpaRepository<Event, Integer> {
	Optional<Event> findByTitle(String title);
}

@Repository
interface TeamRepository extends JpaRepository<Team, Integer> {
	List<Team> findByUserId(int userId);
	List<Team> findByTeamId(int teamId);
}

@Repository
interface ApplicationRepository extends JpaRepository<Application, Integer> {
	Optional<Application> findByUserIdAndEventId(int userId, int eventId);
}

@Repository
interface IdeaRepository extends JpaRepository<Idea, Integer> {}

@Entity
@Table(name = "logins")
class Login {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(nullable = false)
	private String user;

	@Column(nullable = false)
	private String password;

	@Column(nullable = false)
	private String role;

	public int getId() { return id; }
	public String getUser() { return user; }
	public String getPassword() { return password; }
	public String getRole() { return role; }

	public void setId(int id) { this.id = id; }
	public void setUser(String user) { this.user = user; }
	public void setPassword(String password) { this.password = password; }
	public void setRole(String role) { this.role = role; }
}

@Entity
@Table(name = "events")
class Event {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column
	private String title;

	@Column
	private String description;

	@Lob
	private byte[] image;

	@Column
	private String status;

	@Column
	private String comment;

	public int getId() { return id; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }
	public byte[] getImage() { return image; }
	public String getStatus() { return status; }
	public String getComment() { return comment; }

	public void setId(int id) { this.id = id; }
	public void setTitle(String title) { this.title = title; }
	public void setDescription(String description) { this.description = description; }
	public void setImage(byte[] image) { this.image = image; }
	public void setStatus(String status) { this.status = status; }
	public void setComment(String comment) { this.comment = comment; }
}

@Entity
@Table(name = "teams")
class Team {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(name = "user_id")
	private int userId;

	@Column(name = "event_id")
	private int eventId;

	@Column(name = "team_id")
	private int teamId;

	public int getId() { return id; }
	public int getUserId() { return userId; }
	public int getEventId() { return eventId; }
	public int getTeamId() { return teamId; }

	public void setId(int id) { this.id = id; }
	public void setUserId(int userId) { this.userId = userId; }
	public void setEventId(int eventId) { this.eventId = eventId; }
	public void setTeamId(int teamId) { this.teamId = teamId; }
}

@Entity
@Table(name = "applications")
class Application {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(name = "user_id", nullable = false)
	private int userId;

	@Column(name = "event_id", nullable = false)
	private int eventId;

	public int getId() { return id; }
	public int getUserId() { return userId; }
	public int getEventId() { return eventId; }

	public void setId(int id) { this.id = id; }
	public void setUserId(int userId) { this.userId = userId; }
	public void setEventId(int eventId) { this.eventId = eventId; }
}

@Entity
@Table(name = "ideas")
class Idea {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private int id;

	@Column(name = "user_id", nullable = false)
	private int userId;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String description;

	public int getId() { return id; }
	public int getUserId() { return userId; }
	public String getTitle() { return title; }
	public String getDescription() { return description; }

	public void setId(int id) { this.id = id; }
	public void setUserId(int userId) { this.userId = userId; }
	public void setTitle(String title) { this.title = title; }
	public void setDescription(String description) { this.description = description; }
}