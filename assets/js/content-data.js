/* =======================================================================
   VITC — Bundled content for Testimonials & Blog
   The website reads these two sections from the Spring Boot API
   (/testimonials/approved and /blog-posts/published). This file mirrors the
   same shape so the pages still render real VITC content when the backend
   is not running (static hosting / offline preview).
   The API always wins when it responds with data.
   ======================================================================= */
(function (window) {
  "use strict";

  /* Same records that backend/src/main/resources/catalog-seed.json seeds
     into the testimonials table — kept in sync, not duplicated content. */
  var testimonials = [
    {
      name: "Kartikesh Padhi", role: "Advanced Excel & Tally Student",
      course: "Advanced Excel & Tally Prime", category: "account",
      photoUrl: "assets/img/reviewers/kartikesh-padhi.png", rating: 5,
      message: "Learning Advanced Excel and Tally at Vandana IT Courses was honestly a great experience. The trainers explained everything in a super simple way, and the hands-on practice made it way easier to actually understand stuff. Doubts were never a problem\u2014they were always ready to help. If you wanna learn useful skills without boring lectures, this place is solid.",
      approved: true
    },
    {
      name: "Hmua Hualngo", role: "Basic Computer & Advanced Excel Student",
      course: "Basic Computer + Advanced Excel", category: "account",
      photoUrl: "assets/img/reviewers/hmua-hualngo.png", rating: 5,
      message: "I completed the Basic Computer Course and Advance Excel course in Vandana IT Computer Centre, I am recommending this Centre for those who are applying for All India Level Job and State level Job. You will get a Central Government Approved Certificate and Marksheet. The Instructor teaches with professional experiences and skills. Thank you Dattaprasad sir.",
      approved: true
    },
    {
      name: "Neha Gaikwad", role: "Programming Student",
      course: "Programming Fundamentals", category: "programming",
      photoUrl: "assets/img/reviewers/neha-gaikwad.png", rating: 4,
      message: "Excellent course! The explanations were clear and the interactive coding exercises really helped solidify the concepts. Great for beginners.",
      approved: true
    },
    {
      name: "Chaitanya", role: "Hardware & Networking Student",
      course: "Hardware & Networking", category: "cloud",
      photoUrl: "assets/img/reviewers/chaitanya.png", rating: 5,
      message: "#Hardware #Networking #Uran #Computer_Class_VITC #VITC It was a wonderful experience as I got to learn a lot of new things and there is more opportunities as I have been given chances for internships to learn more and experience the ways of hardware through internships.",
      approved: true
    },
    {
      name: "Inder kumar Ram", role: "Advanced Excel Student",
      course: "Advanced Excel", category: "account",
      photoUrl: "assets/img/reviewers/inder-kumar-ram.png", rating: 5,
      message: "I was being taught Advanced Excel and practical example is pretty good if anyone want to join it's good.",
      approved: true
    },
    {
      name: "Sparsh Raut", role: "VITC Student",
      course: "Computer Fundamentals", category: "programming",
      photoUrl: "assets/img/reviewers/sparsh-raut.png", rating: 5,
      message: "Best Experience. And also excellent teaching. High Configuration Computer Lab.",
      approved: true
    },
    {
      name: "Tanvi Mhatre", role: "Coding Student",
      course: "Full Stack Development", category: "fullstack",
      photoUrl: "assets/img/reviewers/tanvi-mhatre.png", rating: 5,
      message: "Excellent coding classes with clear concepts, hands-on projects, and strong fundamentals. Highly recommended.",
      approved: true
    },
    {
      name: "Bhavesh Gawand", role: "VITC Student",
      course: "Computer Fundamentals", category: "programming",
      photoUrl: "assets/img/reviewers/bhavesh-gawand.png", rating: 5,
      message: "I had a very good experience learning here, and I strongly recommend others to experience it \uD83D\uDC4D",
      approved: true
    }
  ];

  function p(text) { return "<p>" + text + "</p>"; }
  function h(text) { return "<h2>" + text + "</h2>"; }
  function ul(items) { return "<ul>" + items.map(function (i) { return "<li>" + i + "</li>"; }).join("") + "</ul>"; }

  var blogPosts = [
    {
      slug: "how-to-choose-your-first-it-career-path",
      title: "How to Choose Your First IT Career Path",
      excerpt: "Confused between development, data, cloud and cyber security? Here is a simple framework our mentors use with every new VITC student.",
      coverImageUrl: "assets/img/blog/career-path.jpg",
      author: "VITC Mentor Team",
      tags: "Career Guidance",
      category: "Career Guidance",
      published: true,
      publishedAt: "2026-01-12T09:30:00",
      content:
        p("Every year we meet students in Uran and Navi Mumbai who know they want an IT career but have no idea which door to walk through. The good news: you do not need to decide forever. You only need to decide what to learn first.") +
        h("1. Start from the work, not the job title") +
        p("Ask yourself which of these you would happily do for eight hours: building screens people click, finding patterns inside data, keeping servers and networks alive, or protecting systems from attackers. Your honest answer already narrows four fields down to one.") +
        h("2. Match the path to your background") +
        ul([
          "Commerce background: Advanced Excel, Tally Prime and Data Analytics are the fastest bridge.",
          "Science / engineering: Full Stack Development, Python or Data Science &amp; AI.",
          "Creative mindset: Graphic Designing or Digital Marketing.",
          "Hands-on, practical mindset: Hardware, Networking and Cloud Computing."
        ]) +
        h("3. Test before you commit") +
        p("Spend two weeks on a free tutorial in your shortlisted field. If you still enjoy it on day fourteen, enrol. At VITC every course begins with a free counselling session precisely so you do not waste a year on the wrong track.") +
        h("4. Plan for the first job, not the dream job") +
        p("Your first role only needs to get you inside the industry. Choose the path with the shortest credible route to a junior role, then grow. Most of our placed students switched specialisation once within three years \u2014 and that is perfectly normal.")
    },
    {
      slug: "python-vs-java-2026",
      title: "Python vs Java in 2026: Which One Should You Learn?",
      excerpt: "Both languages hire well in India. The right choice depends on the kind of team you want to join \u2014 here is an honest comparison.",
      coverImageUrl: "assets/img/blog/python-vs-java.jpg",
      author: "Dattaprasad Sir",
      tags: "Programming",
      category: "Programming",
      published: true,
      publishedAt: "2026-01-05T10:00:00",
      content:
        p("This is the single most common question in our counselling room. The short answer: learn Python if you want speed and variety, learn Java if you want enterprise depth and stability.") +
        h("Where Python wins") +
        ul([
          "Data science, machine learning and AI work is Python-first.",
          "Automation, scripting and quick prototypes.",
          "Gentler syntax \u2014 beginners write useful programs within weeks."
        ]) +
        h("Where Java wins") +
        ul([
          "Large banking, insurance and product companies still run on Java and Spring Boot.",
          "Android development and huge backend systems.",
          "Strong typing teaches discipline that transfers to every other language."
        ]) +
        h("Our recommendation") +
        p("If you are targeting analytics, AI or a fast start, begin with Python. If you are targeting a service company or a core backend role, begin with Java and Spring Boot. Whichever you pick, finish it properly \u2014 recruiters hire depth, not a list of half-learned languages.")
    },
    {
      slug: "ai-skills-every-fresher-should-learn",
      title: "AI Skills Every Fresher Should Learn",
      excerpt: "You do not need a PhD to work with AI. These practical skills are what hiring managers actually ask freshers about in 2026.",
      coverImageUrl: "assets/img/blog/ai-skills.jpg",
      author: "VITC Data Science Faculty",
      tags: "Artificial Intelligence",
      category: "Artificial Intelligence",
      published: true,
      publishedAt: "2025-12-22T11:15:00",
      content:
        p("AI has stopped being a specialist subject. Marketing executives, accountants and support engineers are all expected to use it. Here is the realistic skill list for a fresher.") +
        h("The core four") +
        ul([
          "Python basics \u2014 lists, functions, files and pandas.",
          "Data cleaning \u2014 eighty percent of real AI work is preparing data.",
          "Prompt engineering \u2014 writing precise instructions for language models and validating the output.",
          "Model intuition \u2014 knowing when classification, regression or clustering fits the problem."
        ]) +
        h("What matters more than the algorithm") +
        p("Show that you can explain a result to a non-technical person. In interviews we see candidates who can train a model but cannot say why the business should trust it. Practise writing a five-line summary of every project you build.") +
        h("Build three small projects") +
        p("A sales forecast, a text classifier and a simple recommendation tool are enough to demonstrate range. Publish them with a clear README \u2014 that repository becomes your portfolio.")
    },
    {
      slug: "building-your-first-data-analytics-portfolio",
      title: "Building Your First Data Analytics Portfolio",
      excerpt: "Three well-documented projects beat ten certificates. Here is exactly what to build and how to present it.",
      coverImageUrl: "assets/img/blog/data-portfolio.jpg",
      author: "VITC Data Science Faculty",
      tags: "Data Science",
      category: "Data Science",
      published: true,
      publishedAt: "2025-12-08T09:00:00",
      content:
        p("A portfolio is proof of work. Certificates say you attended; a portfolio says you can deliver. Here is the structure we ask every VITC analytics student to follow.") +
        h("Project 1 \u2014 the clean-up") +
        p("Take a messy public dataset, document every cleaning decision, and publish a before-and-after summary. This shows discipline, which is what analytics teams hire for.") +
        h("Project 2 \u2014 the dashboard") +
        p("Build an interactive dashboard in Excel, Power BI or Python. Pick a topic you can talk about confidently: local business sales, cricket statistics, college admissions.") +
        h("Project 3 \u2014 the recommendation") +
        p("End with a project that concludes in a decision: which product to stock, which customers to retain. Analysts are paid for recommendations, not charts.") +
        h("How to present it") +
        ul([
          "One page per project: problem, data, method, result.",
          "Screenshots at the top \u2014 recruiters scan before they read.",
          "A link in your resume header, not buried at the bottom."
        ])
    },
    {
      slug: "seo-for-beginners-30-day-roadmap",
      title: "SEO for Beginners: A 30-Day Roadmap",
      excerpt: "A week-by-week plan to go from zero to running real search campaigns \u2014 the same roadmap we use in our Digital Marketing batch.",
      coverImageUrl: "assets/img/blog/seo-roadmap.jpg",
      author: "VITC Digital Marketing Faculty",
      tags: "Digital Marketing",
      category: "Digital Marketing",
      published: true,
      publishedAt: "2025-11-26T08:45:00",
      content:
        p("SEO rewards consistency more than talent. Follow this thirty-day plan on one real website \u2014 your own blog, a family business, or a college club page.") +
        h("Week 1 \u2014 foundations") +
        ul(["How search engines crawl, index and rank.", "Set up Google Search Console and Analytics.", "Run your first technical audit."]) +
        h("Week 2 \u2014 keywords") +
        ul(["Build a keyword sheet with intent columns.", "Map one keyword to one page.", "Study the competitors ranking in the top five."]) +
        h("Week 3 \u2014 on-page") +
        ul(["Rewrite titles and meta descriptions.", "Fix heading structure and internal links.", "Compress images and improve page speed."]) +
        h("Week 4 \u2014 off-page and reporting") +
        ul(["Local listings and Google Business Profile.", "Earn your first three genuine backlinks.", "Build a one-page monthly report."]) +
        p("At the end of the month you will have measurable rankings to show in an interview \u2014 far more convincing than a theory certificate.")
    },
    {
      slug: "resume-and-interview-tips-for-it-freshers",
      title: "Resume and Interview Tips for IT Freshers",
      excerpt: "What recruiters in Navi Mumbai actually look for in a fresher resume, and how to answer the questions that decide the offer.",
      coverImageUrl: "assets/img/blog/resume-interview.jpg",
      author: "VITC Placement Cell",
      tags: "Resume Tips",
      category: "Resume Tips",
      published: true,
      publishedAt: "2025-11-10T10:30:00",
      content:
        p("Our placement cell reviews hundreds of fresher resumes every year. The same fixable mistakes appear again and again.") +
        h("The resume") +
        ul([
          "One page. Always.",
          "Projects above education \u2014 they are your only real evidence.",
          "Write results, not duties: \u201creduced report time from 2 hours to 10 minutes\u201d.",
          "Skills section split into Strong and Familiar \u2014 honesty survives the technical round.",
          "No photo, no age, no marital status, no colourful template."
        ]) +
        h("The interview") +
        p("Prepare a two-minute introduction, one deep project story, and three questions to ask them. When you do not know an answer, say so and explain how you would find out \u2014 interviewers respect that far more than a guess.") +
        h("After the interview") +
        p("Send a short thank-you email the same day. It costs nothing and it is remembered surprisingly often.")
    }
  ];

  window.VITC_STATIC = { testimonials: testimonials, blogPosts: blogPosts };
})(window);
